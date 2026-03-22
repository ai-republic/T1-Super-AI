package com.airepublic.t1.agent;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.openai.OpenAiImageOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

import java.util.Base64;

import com.airepublic.t1.cli.CLIFormatter;
import com.airepublic.t1.model.AgentConfiguration.TaskType;
import com.airepublic.t1.config.AgentConfigurationManager;
import com.airepublic.t1.memory.VectorMemoryService;
import com.airepublic.t1.model.AgentConfiguration;
import com.airepublic.t1.model.ConversationMessage;
import com.airepublic.t1.model.MessageAttachment;
import com.airepublic.t1.session.SessionContextManager;
import com.airepublic.t1.tools.AgentTool;
import com.airepublic.t1.tools.AutoModelSelectorTool;
import com.airepublic.t1.service.MessageBroadcaster;
import com.airepublic.t1.service.MemoryManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentOrchestrator {
    private final AgentConfigurationManager configManager;
    private final ToolRegistry toolRegistry;
    private final LLMClientFactory llmClientFactory;
    private final ImageModelFactory imageModelFactory;
    private final CLIFormatter formatter;
    private final SessionContextManager sessionContextManager;
    private final AutoModelSelectorTool autoModelSelectorTool;
    private final MessageBroadcaster messageBroadcaster;

    @Autowired(required = false)
    private VectorMemoryService memoryService;

    @Autowired(required = false)
    private AgentManager agentManager;

    @Autowired(required = false)
    private MemoryManager memoryManager;

    private boolean autoModelSelectionEnabled = true; // Default enabled

    private static final int MAX_TOOL_ITERATIONS = 10;
    private String currentModelInfo = null; // Store current model being used

    // CRITICAL: ThreadLocal storage for complete agent isolation
    // Each thread gets its own conversation history, session context, agent name, and panel ID
    // This ensures complete isolation between concurrent agent requests
    private static final ThreadLocal<List<ConversationMessage>> THREAD_CONVERSATION_HISTORY =
        ThreadLocal.withInitial(CopyOnWriteArrayList::new);
    private static final ThreadLocal<String> THREAD_SESSION_CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_AGENT_CONTEXT = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_PANEL_ID = new ThreadLocal<>();
    private static final ThreadLocal<MessageAttachment> GENERATED_IMAGE_ATTACHMENT = new ThreadLocal<>();

    // Fallback global storage for backward compatibility (used only when ThreadLocal is not set)
    // This should rarely be used in normal operation
    private final List<ConversationMessage> fallbackConversationHistory = new CopyOnWriteArrayList<>();
    private String fallbackSessionContext = null;

    /**
     * Set the agent context for the current thread.
     * This must be called before processing a message to ensure proper agent isolation.
     * Initializes a fresh conversation history and loads the agent's session context.
     */
    public void setThreadAgentContext(String agentName, String panelId) {
        CURRENT_AGENT_CONTEXT.set(agentName);
        CURRENT_PANEL_ID.set(panelId);

        // Initialize fresh conversation history for this thread
        THREAD_CONVERSATION_HISTORY.get().clear();

        // Load agent-specific session context
        if (agentName != null) {
            String sessionCtx = sessionContextManager.buildInitialContext(agentName);
            THREAD_SESSION_CONTEXT.set(sessionCtx);
            log.debug("Thread {} loaded session context for agent '{}'",
                     Thread.currentThread().getName(), agentName);
        } else {
            String sessionCtx = sessionContextManager.buildInitialContext();
            THREAD_SESSION_CONTEXT.set(sessionCtx);
        }

        log.info("🔧 Thread {} initialized context - Agent: {}, Panel: {}",
                  Thread.currentThread().getName(), agentName, panelId);
    }

    /**
     * Clear the agent context for the current thread.
     * This should always be called in a finally block after processing.
     * CRITICAL: Prevents memory leaks in thread pools.
     */
    public void clearThreadAgentContext() {
        String agent = CURRENT_AGENT_CONTEXT.get();
        String panel = CURRENT_PANEL_ID.get();

        // Clear all thread-local storage
        THREAD_CONVERSATION_HISTORY.remove();
        THREAD_SESSION_CONTEXT.remove();
        CURRENT_AGENT_CONTEXT.remove();
        CURRENT_PANEL_ID.remove();
        GENERATED_IMAGE_ATTACHMENT.remove();

        log.debug("🧹 Thread {} cleared context - Agent: {}, Panel: {}",
                  Thread.currentThread().getName(), agent, panel);
    }

    /**
     * Get the current thread's agent name
     */
    public String getCurrentThreadAgent() {
        return CURRENT_AGENT_CONTEXT.get();
    }

    /**
     * Get the current thread's panel ID
     */
    public String getCurrentThreadPanelId() {
        return CURRENT_PANEL_ID.get();
    }

    /**
     * Get the current panel ID statically (for use by tools).
     * Returns panel ID or thread name as fallback.
     */
    public static String getPanelIdForCurrentThread() {
        String panelId = CURRENT_PANEL_ID.get();
        return panelId != null ? panelId : Thread.currentThread().getName();
    }

    /**
     * Get the conversation history for the current thread.
     * Returns thread-local history if available, otherwise fallback global history.
     */
    private List<ConversationMessage> getConversationHistory() {
        // Use thread-local if agent context is set
        if (CURRENT_AGENT_CONTEXT.get() != null) {
            return THREAD_CONVERSATION_HISTORY.get();
        }
        // Fallback to global for backward compatibility
        return fallbackConversationHistory;
    }

    /**
     * Get the session context for the current thread.
     * Returns thread-local context if available, otherwise fallback global context.
     */
    private String getSessionContext() {
        // Use thread-local if agent context is set
        if (CURRENT_AGENT_CONTEXT.get() != null) {
            return THREAD_SESSION_CONTEXT.get();
        }
        // Fallback to global for backward compatibility
        return fallbackSessionContext;
    }

    /**
     * Set the session context for the current thread.
     */
    private void setSessionContext(String context) {
        // Set thread-local if agent context is set
        if (CURRENT_AGENT_CONTEXT.get() != null) {
            THREAD_SESSION_CONTEXT.set(context);
        } else {
            // Fallback to global for backward compatibility
            fallbackSessionContext = context;
        }
    }

    public String processMessage(final String userMessage) {
        return processMessage(userMessage, new ArrayList<>());
    }

    public String processMessage(final String userMessage, final List<MessageAttachment> attachments) {
        // Log with thread-specific agent context if available
        String threadAgent = CURRENT_AGENT_CONTEXT.get();
        String threadPanel = CURRENT_PANEL_ID.get();
        log.info("🚀 Processing message - Thread: {}, Agent: {}, Panel: {}, Attachments: {}",
                 Thread.currentThread().getName(), threadAgent, threadPanel,
                 attachments != null ? attachments.size() : 0);

        // Add user message to history with attachments
        final ConversationMessage userMsg = ConversationMessage.builder()
                .role("user")
                .content(userMessage)
                .timestamp(LocalDateTime.now())
                .attachments(attachments != null ? attachments : new ArrayList<>())
                .build();
        getConversationHistory().add(userMsg);

        try {
            // Get the appropriate chat client based on automatic model selection
            log.debug("Selecting chat client for message");
            final ChatClient chatClient = selectChatClient(userMessage);
            log.info("✅ Selected chat client");

            // Search for relevant past context if memory service is available
            String contextPrefix = "";
            if (memoryService != null) {
                final String relevantContext = memoryService.getRelevantContext(userMessage, 3);
                if (!relevantContext.isEmpty()) {
                    contextPrefix = relevantContext + "\n---\nCurrent query:\n";
                    log.debug("📚 Retrieved {} characters of relevant context", relevantContext.length());
                }
            }

            // Conversation loop with tool calling
            log.info("🔄 Starting conversation loop with enhanced message");
            final String finalResponse = runConversationLoop(chatClient, contextPrefix + userMessage);
            log.info("✅ Conversation loop completed, response length: {}", finalResponse != null ? finalResponse.length() : 0);

            // Add assistant response to history
            // Use thread-local agent context if available, otherwise fall back to global
            final String currentAgent = CURRENT_AGENT_CONTEXT.get() != null ?
                CURRENT_AGENT_CONTEXT.get() :
                (agentManager != null ? agentManager.getCurrentAgentName() : null);

            // Check if there's a generated image attachment to add
            final MessageAttachment imageAttachment = GENERATED_IMAGE_ATTACHMENT.get();
            final List<MessageAttachment> messageAttachments = imageAttachment != null ?
                    List.of(imageAttachment) : null;

            if (imageAttachment != null) {
                log.info("📎 DEBUG: Image attachment found in ThreadLocal:");
                log.info("   - Filename: {}", imageAttachment.getFilename());
                log.info("   - MIME Type: {}", imageAttachment.getMimeType());
                log.info("   - Has base64: {}", imageAttachment.getContentBase64() != null);
                log.info("   - Base64 length: {}", imageAttachment.getContentBase64() != null ? imageAttachment.getContentBase64().length() : 0);
            } else {
                log.warn("📎 DEBUG: No image attachment found in ThreadLocal");
            }

            final ConversationMessage assistantMsg = ConversationMessage.builder()
                    .role("assistant")
                    .content(finalResponse)
                    .agentName(currentAgent)
                    .timestamp(LocalDateTime.now())
                    .modelUsed(configManager.getFallbackModel())
                    .attachments(messageAttachments)
                    .build();
            getConversationHistory().add(assistantMsg);

            // Clear the image attachment ThreadLocal after using it
            if (imageAttachment != null) {
                log.info("📎 Added image attachment to assistant message");
                GENERATED_IMAGE_ATTACHMENT.remove();
            }

            // Store in vector memory if available
            if (memoryService != null) {
                final AgentConfiguration config = configManager.getConfiguration();
                // Use GENERAL_KNOWLEDGE as fallback for memory storage
                final AgentConfiguration.TaskModelConfig fallbackConfig =
                        config.getTaskModels().get(AgentConfiguration.TaskType.GENERAL_KNOWLEDGE);
                if (fallbackConfig != null) {
                    memoryService.storePrompt(
                            userMessage,
                            finalResponse,
                            fallbackConfig.getProvider().toString(),
                            fallbackConfig.getModel()
                    );
                }
            }

            // Save conversation to agent memory (MEMORY.md)
            if (memoryManager != null && currentAgent != null) {
                try {
                    memoryManager.appendConversation(currentAgent, userMessage, finalResponse);
                    log.debug("💾 Saved conversation to memory for agent '{}'", currentAgent);
                } catch (Exception e) {
                    log.error("Failed to save conversation to memory for agent '{}'", currentAgent, e);
                }
            }

            return finalResponse;

        } catch (final Exception e) {
            log.error("Error processing message", e);
            return "Error: " + e.getMessage();
        }
    }

    /**
     * Handles image generation requests using ImageModel.
     * Returns the raw image data as an attachment so the UI can display it.
     *
     * @param userMessage The user's image generation prompt
     * @return Simple confirmation message (actual image is in attachments)
     */
    private String handleImageGeneration(final String userMessage) {
        try {
            // Get the image model
            final ImageModel imageModel = imageModelFactory.getImageModelForTask();
            log.info("🎨 Using ImageModel for image generation");

            // Create image prompt (default options are set in ImageModelFactory)
            final ImagePrompt imagePrompt = new ImagePrompt(userMessage);

            // Generate image
            log.info("🖼️ Generating image with prompt: {}", userMessage);
            final ImageResponse response = imageModel.call(imagePrompt);

            // Extract image data (URL or base64)
            if (response != null && response.getResults() != null && !response.getResults().isEmpty()) {
                final var imageOutput = response.getResult().getOutput();

                // Check if response has URL (DALL-E models) or base64 (gpt-image models)
                if (imageOutput.getUrl() != null && !imageOutput.getUrl().isEmpty()) {
                    final String imageUrl = imageOutput.getUrl();
                    log.info("✅ Image generated successfully with URL: {}", imageUrl);

                    // For URL responses, return the URL for the UI to handle
                    return "I've generated an image based on your prompt.\n\nImage URL: " + imageUrl;

                } else if (imageOutput.getB64Json() != null && !imageOutput.getB64Json().isEmpty()) {
                    final String base64Data = imageOutput.getB64Json();
                    log.info("✅ Image generated successfully as base64 ({} characters)", base64Data.length());

                    // Create MessageAttachment with the image data
                    final MessageAttachment imageAttachment = MessageAttachment.builder()
                            .id(java.util.UUID.randomUUID().toString())
                            .filename("generated_image_" + System.currentTimeMillis() + ".png")
                            .mimeType("image/png")
                            .contentBase64(base64Data)
                            .fileSize((long) base64Data.length())
                            .description("Generated image: " + userMessage)
                            .build();

                    // Add the image as attachment to the current conversation
                    // Store in thread-local for the calling code to retrieve
                    GENERATED_IMAGE_ATTACHMENT.set(imageAttachment);

                    log.info("✅ Image attachment created and stored");
                    return "I've generated an image based on your prompt. The image is attached to this message.";

                } else {
                    log.error("Image generation returned result but no URL or base64 data");
                    return "Error: Image generation failed - no image data in response";
                }
            } else {
                log.error("Image generation returned no results");
                return "Error: Image generation failed - no results returned";
            }

        } catch (final Exception e) {
            log.error("Error during image generation", e);
            return "Error generating image: " + e.getMessage();
        }
    }

    /**
     * Selects the appropriate chat client based on automatic model selection.
     * If auto-selection is enabled, analyzes the prompt to detect task type.
     * Falls back to default provider if disabled or no task-specific model found.
     */
    private ChatClient selectChatClient(final String userMessage) {
        // Clear previous model info
        currentModelInfo = null;

        // If auto-selection is disabled, use GENERAL_KNOWLEDGE fallback
        if (!autoModelSelectionEnabled) {
            log.debug("Auto model selection disabled, using GENERAL_KNOWLEDGE fallback");
            return llmClientFactory.getChatClientForTask(TaskType.GENERAL_KNOWLEDGE);
        }

        // Try to detect task type from prompt
        final Optional<TaskType> detectedTaskType = autoModelSelectorTool.selectTaskTypeForPrompt(userMessage);

        if (detectedTaskType.isEmpty()) {
            log.debug("No specific task type detected, using GENERAL_KNOWLEDGE fallback");
            return llmClientFactory.getChatClientForTask(TaskType.GENERAL_KNOWLEDGE);
        }

        final TaskType taskType = detectedTaskType.get();
        log.debug("Detected task type: {}", taskType);

        // IMAGE_GENERATION should be handled via tools, not direct model selection
        // Use GENERAL_KNOWLEDGE chat client so the agent can call execute_with_task_model tool
        if (taskType == TaskType.IMAGE_GENERATION) {
            log.info("IMAGE_GENERATION detected - using GENERAL_KNOWLEDGE chat client (agent will use tools for image generation)");
            return llmClientFactory.getChatClientForTask(TaskType.GENERAL_KNOWLEDGE);
        }

        // Use task-specific model (getChatClientForTask will log and handle fallback)
        return llmClientFactory.getChatClientForTask(taskType);
    }

    /**
     * Returns the current model info string for display purposes.
     * Returns null if using default model or no task-specific model was selected.
     */
    public String getCurrentModelInfo() {
        return currentModelInfo;
    }

    /**
     * Enables or disables automatic model selection.
     */
    public void setAutoModelSelectionEnabled(boolean enabled) {
        this.autoModelSelectionEnabled = enabled;
        log.info("Auto model selection {}", enabled ? "enabled" : "disabled");
    }

    /**
     * Returns whether automatic model selection is currently enabled.
     */
    public boolean isAutoModelSelectionEnabled() {
        return autoModelSelectionEnabled;
    }

    private String runConversationLoop(final ChatClient chatClient, final String enhancedUserMessage) {
        final List<Message> messages = convertHistoryToMessages();

        log.info("📝 Replacing last message with enhanced version (preserving media)");

        // Replace the last user message with the enhanced one (with context)
        // BUT preserve any media attachments from the original message
        if (!messages.isEmpty() && messages.get(messages.size() - 1) instanceof UserMessage lastUserMessage) {
            try {
                // Check if the last user message has media attachments
                if (lastUserMessage.getMedia() != null && !lastUserMessage.getMedia().isEmpty()) {
                    log.info("🖼️ Found {} media attachment(s) to preserve", lastUserMessage.getMedia().size());

                    // Preserve media attachments when replacing with enhanced message
                    var enhancedMessageBuilder = UserMessage.builder()
                            .text(enhancedUserMessage);

                    // Add all media from the original message
                    int mediaCount = 0;
                    for (var media : lastUserMessage.getMedia()) {
                        enhancedMessageBuilder.media(media);
                        mediaCount++;
                        log.debug("Added media {} to enhanced message", mediaCount);
                    }

                    log.info("✅ Building enhanced message with {} media attachment(s)", mediaCount);
                    messages.set(messages.size() - 1, enhancedMessageBuilder.build());
                    log.info("✅ Enhanced message with media set successfully");
                } else {
                    log.debug("No media attachments, using text-only message");
                    // No media, just replace with text
                    messages.set(messages.size() - 1, new UserMessage(enhancedUserMessage));
                }
            } catch (Exception e) {
                log.error("❌ Error replacing message with enhanced version", e);
                throw new RuntimeException("Failed to enhance message with media", e);
            }
        }

        log.info("📋 Message list has {} messages total", messages.size());
        int iteration = 0;

        // Tools are now registered as default tools in LLMClientFactory
        // No need to pass them with each request
        log.debug("Using default tools registered with ChatClient");

        log.info("🔁 Entering conversation loop (max {} iterations)", MAX_TOOL_ITERATIONS);

        while (iteration < MAX_TOOL_ITERATIONS) {
            iteration++;
            log.info("🔄 Loop iteration {}/{}", iteration, MAX_TOOL_ITERATIONS);

            // Build prompt - tools are automatically available from ChatClient defaults
            log.info("📦 Building prompt with {} messages", messages.size());
            final Prompt prompt = new Prompt(messages);
            log.info("✅ Prompt built successfully");

            try {
                // Call LLM with tools enabled
                log.info("🤖 Calling LLM (iteration {}) with model via ChatClient...", iteration);

                final ChatResponse response;
                try {
                    long llmStart = System.currentTimeMillis();
                    // Use call() for non-streaming response
                    // The chatResponse() method waits for the complete response
                    response = chatClient.prompt(prompt)
                            .call()
                            .chatResponse();
                    long llmTime = System.currentTimeMillis() - llmStart;
                    log.info("✅ LLM responded successfully in {}ms", llmTime);

                    // Check if any tools generated an image attachment during LLM execution
                    // Spring AI executes tools as function callbacks, so we check after the call completes
                    final MessageAttachment imageAttachment =
                        com.airepublic.t1.tools.ExecuteWithTaskModelTool.getLatestGeneratedImageAttachment();
                    if (imageAttachment != null) {
                        log.info("📎 Image attachment detected after LLM call");
                        log.info("   - Filename: {}", imageAttachment.getFilename());
                        log.info("   - MIME Type: {}", imageAttachment.getMimeType());
                        log.info("   - Has base64: {}", imageAttachment.getContentBase64() != null);
                        // Store in AgentOrchestrator's ThreadLocal for later retrieval
                        GENERATED_IMAGE_ATTACHMENT.set(imageAttachment);
                        // Clear the tool's atomic reference
                        com.airepublic.t1.tools.ExecuteWithTaskModelTool.clearLatestGeneratedImageAttachment();
                    }

                } catch (Exception llmError) {
                    log.error("❌ LLM call failed: {}", llmError.getMessage(), llmError);
                    throw new RuntimeException("LLM API call failed: " + llmError.getMessage(), llmError);
                }

                log.info("📝 Processing LLM response...");
                final AssistantMessage assistantMessage = response.getResult().getOutput();
                log.info("✅ Got assistant message, adding to conversation");
                messages.add(assistantMessage);
                log.info("✅ Message added to list");

                // Check if there are tool calls
                if (assistantMessage.getToolCalls() == null || assistantMessage.getToolCalls().isEmpty()) {
                    // No more tool calls, return final response
                    return assistantMessage.getText() != null ? assistantMessage.getText() : "";
                }

                // Execute tool calls
                final List<ToolResponseMessage> toolResponses = new ArrayList<>();

                for (final var toolCall : assistantMessage.getToolCalls()) {
                    final String toolName = toolCall.name();
                    // In Spring AI 2.0, arguments() might return a String
                    final String argumentsStr = toolCall.arguments() != null ? toolCall.arguments().toString() : "{}";

                    formatter.printToolCall(toolName, argumentsStr);

                    try {
                        final AgentTool tool = toolRegistry.getTool(toolName);
                        if (tool == null) {
                            throw new IllegalArgumentException("Tool not found: " + toolName);
                        }

                        // Parse arguments if they're a String
                        Map<String, Object> argsMap;
                        final Object args = toolCall.arguments();
                        if (args instanceof Map) {
                            argsMap = (Map<String, Object>) args;
                        } else {
                            // Try to parse as JSON string
                            try {
                                argsMap = new com.fasterxml.jackson.databind.ObjectMapper()
                                        .readValue(args.toString(), Map.class);
                            } catch (final Exception ex) {
                                argsMap = new HashMap<>();
                            }
                        }

                        // Broadcast tool call to UI - use thread-local agent context
                        final String currentAgent = CURRENT_AGENT_CONTEXT.get() != null ?
                            CURRENT_AGENT_CONTEXT.get() :
                            (agentManager != null ? agentManager.getCurrentAgentName() : "default");
                        messageBroadcaster.broadcastToolCall(currentAgent, toolName, argsMap);

                        final long startTime = System.currentTimeMillis();
                        final String result = tool.execute(argsMap);
                        final long executionTime = System.currentTimeMillis() - startTime;

                        formatter.printToolCall(toolName, result);

                        // Check if the tool generated an image attachment
                        // This happens with execute_with_task_model tool for IMAGE_GENERATION
                        if ("execute_with_task_model".equals(toolName)) {
                            final MessageAttachment imageAttachment =
                                com.airepublic.t1.tools.ExecuteWithTaskModelTool.getLatestGeneratedImageAttachment();
                            if (imageAttachment != null) {
                                log.info("📎 Image attachment detected from tool execution");
                                log.info("   - Filename: {}", imageAttachment.getFilename());
                                log.info("   - MIME Type: {}", imageAttachment.getMimeType());
                                log.info("   - Has base64: {}", imageAttachment.getContentBase64() != null);
                                // Store in AgentOrchestrator's ThreadLocal for later retrieval
                                GENERATED_IMAGE_ATTACHMENT.set(imageAttachment);
                                // Clear the tool's atomic reference
                                com.airepublic.t1.tools.ExecuteWithTaskModelTool.clearLatestGeneratedImageAttachment();
                            } else {
                                log.warn("📎 No image attachment from execute_with_task_model tool");
                            }
                        }

                        // Broadcast tool result to UI
                        messageBroadcaster.broadcastToolResult(currentAgent, toolName, result, true, executionTime);

                        // Store tool action in vector memory if available
                        if (memoryService != null) {
                            memoryService.storeAction(toolName, argumentsStr, result);
                        }

                        // Spring AI 2.0: Use builder for ToolResponseMessage
                        final ToolResponseMessage.ToolResponse toolResponse =
                                new ToolResponseMessage.ToolResponse(toolCall.id(), toolName, result);
                        toolResponses.add(ToolResponseMessage.builder()
                                .responses(List.of(toolResponse))
                                .build());

                    } catch (final Exception e) {
                        log.error("Error executing tool: {}", toolName, e);
                        final String errorMsg = "Error executing tool: " + e.getMessage();
                        formatter.printError(errorMsg);

                        // Broadcast tool failure to UI - use thread-local agent context
                        final String currentAgent = CURRENT_AGENT_CONTEXT.get() != null ?
                            CURRENT_AGENT_CONTEXT.get() :
                            (agentManager != null ? agentManager.getCurrentAgentName() : "default");
                        messageBroadcaster.broadcastToolResult(currentAgent, toolName, errorMsg, false, 0);

                        final ToolResponseMessage.ToolResponse toolResponse =
                                new ToolResponseMessage.ToolResponse(toolCall.id(), toolName, errorMsg);
                        toolResponses.add(ToolResponseMessage.builder()
                                .responses(List.of(toolResponse))
                                .build());
                    }
                }

                // Add tool responses to messages
                messages.addAll(toolResponses);

            } catch (final Exception e) {
                log.error("Error in conversation loop", e);
                return "Error during conversation: " + e.getMessage();
            }
        }

        return "Maximum tool iterations reached. Please try rephrasing your request.";
    }

    private List<Message> convertHistoryToMessages() {
        final List<Message> messages = new ArrayList<>();

        // Get session context (already loaded in setThreadAgentContext)
        String sessionCtx = getSessionContext();

        // Load session context if not already loaded (fallback for backward compatibility)
        if (sessionCtx == null) {
            // Get current agent name from thread-local context or global state
            final String currentAgent = CURRENT_AGENT_CONTEXT.get() != null ?
                CURRENT_AGENT_CONTEXT.get() :
                (agentManager != null ? agentManager.getCurrentAgentName() : null);
            if (currentAgent != null) {
                // Load agent-specific CHARACTER.md
                sessionCtx = sessionContextManager.buildInitialContext(currentAgent);
                setSessionContext(sessionCtx);
                log.debug("Loaded session context for agent: {}", currentAgent);
            } else {
                // Fallback to loading only USER.md
                sessionCtx = sessionContextManager.buildInitialContext();
                setSessionContext(sessionCtx);
            }
        }

        // Build concise system message with session context
        final StringBuilder systemMessageBuilder = new StringBuilder();

        // Add extracted character profile (concise)
        if (sessionCtx != null && !sessionCtx.isEmpty()) {
            systemMessageBuilder.append(sessionCtx);
        }

        // Add base instructions
        systemMessageBuilder.append("\nYou are an AI assistant that helps users accomplish their tasks.\n");
        systemMessageBuilder.append("Be helpful, clear, and concise in your responses.\n");
        systemMessageBuilder.append("Always explain what you're doing and why.\n");
        systemMessageBuilder.append("Follow the communication style and personality defined in your character profile above.\n");
        systemMessageBuilder.append("Your root folder for all configurations, plugins folder, skills folder and tools is `~/.t1-super-ai/` on linux and '%USERPROFILE%´\\.t1-super-ai\\' on windows\n");
        systemMessageBuilder.append("Be creative and inventitive. Try to solve the problem yourself. If you install a program or create a script, also create a skill, plugin or tool for future use.\n");
        systemMessageBuilder.append("If you need to create or edit a skill, tool or plugin read the USAGE.md file first.\n");

        messages.add(new UserMessage(systemMessageBuilder.toString()));

        // Convert conversation history (using thread-local or fallback)
        for (final ConversationMessage msg : getConversationHistory()) {
            if ("user".equals(msg.getRole())) {
                // Check if message has attachments
                if (msg.getAttachments() != null && !msg.getAttachments().isEmpty()) {
                    log.info("📎 Converting user message with {} attachment(s)", msg.getAttachments().size());
                    // Create UserMessage with media content using builder
                    var userMessageBuilder = UserMessage.builder()
                            .text(msg.getContent());

                    for (MessageAttachment attachment : msg.getAttachments()) {
                        try {
                            MimeType mimeType = parseMimeType(attachment.getMimeType());
                            // Decode base64 content and wrap in ByteArrayResource
                            byte[] content = Base64.getDecoder().decode(attachment.getContentBase64());
                            ByteArrayResource resource = new ByteArrayResource(content);
                            userMessageBuilder.media(new Media(mimeType, resource));
                            log.info("✅ Added media attachment: {} ({}) - {} bytes",
                                    attachment.getFilename(), attachment.getMimeType(), content.length);
                        } catch (Exception e) {
                            log.error("❌ Failed to process attachment: {}", attachment.getFilename(), e);
                        }
                    }
                    messages.add(userMessageBuilder.build());
                    log.info("✅ User message with media added to conversation");
                } else {
                    messages.add(new UserMessage(msg.getContent()));
                }
            } else if ("assistant".equals(msg.getRole())) {
                messages.add(new AssistantMessage(msg.getContent()));
            }
        }

        return messages;
    }


    /**
     * Parse MIME type string to Spring MimeType object
     */
    private MimeType parseMimeType(String mimeTypeStr) {
        if (mimeTypeStr == null) {
            return MimeTypeUtils.APPLICATION_OCTET_STREAM;
        }

        // Common image types
        switch (mimeTypeStr.toLowerCase()) {
            case "image/png":
                return MimeTypeUtils.IMAGE_PNG;
            case "image/jpeg":
            case "image/jpg":
                return MimeTypeUtils.IMAGE_JPEG;
            case "image/gif":
                return MimeTypeUtils.IMAGE_GIF;
            case "text/plain":
                return MimeTypeUtils.TEXT_PLAIN;
            default:
                try {
                    return MimeTypeUtils.parseMimeType(mimeTypeStr);
                } catch (Exception e) {
                    log.warn("Failed to parse MIME type: {}, using default", mimeTypeStr);
                    return MimeTypeUtils.APPLICATION_OCTET_STREAM;
                }
        }
    }

    public void clearHistory() {
        getConversationHistory().clear();
        formatter.printSuccess("Conversation history cleared");
    }

    public void reloadSessionContext() {
        String sessionCtx = sessionContextManager.buildInitialContext();
        setSessionContext(sessionCtx);
        log.info("🔄 Session context reloaded from USER.md and CHARACTER.md");
        formatter.printSuccess("Session context reloaded - CHARACTER and USAGE profiles refreshed");
    }

    /**
     * Reload session context for a specific agent
     */
    public void reloadSessionContext(String agentName) {
        if (agentName == null) {
            log.warn("Cannot reload session context for null agent name");
            return;
        }
        // Load agent-specific CHARACTER.md and USER.md
        String sessionCtx = sessionContextManager.buildInitialContext(agentName);
        setSessionContext(sessionCtx);
        log.info("🔄 Session context reloaded for agent '{}' from USER.md and CHARACTER.md", agentName);
        formatter.printSuccess("Session context reloaded for agent '" + agentName + "'");
    }

    public List<ConversationMessage> getConversationHistoryCopy() {
        // Return a copy to prevent external modification
        List<ConversationMessage> history = getConversationHistory();
        return new ArrayList<>(history);
    }
}

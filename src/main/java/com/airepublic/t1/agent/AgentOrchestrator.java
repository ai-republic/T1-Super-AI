package com.airepublic.t1.agent;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.airepublic.t1.cli.CLIFormatter;
import com.airepublic.t1.model.AgentConfiguration.TaskType;
import com.airepublic.t1.config.AgentConfigurationManager;
import com.airepublic.t1.memory.VectorMemoryService;
import com.airepublic.t1.model.AgentConfiguration;
import com.airepublic.t1.model.ConversationMessage;
import com.airepublic.t1.session.SessionContextManager;
import com.airepublic.t1.tools.AgentTool;
import com.airepublic.t1.tools.AutoModelSelectorTool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentOrchestrator {
    private final AgentConfigurationManager configManager;
    private final ToolRegistry toolRegistry;
    private final LLMClientFactory llmClientFactory;
    private final CLIFormatter formatter;
    private final SessionContextManager sessionContextManager;
    private final AutoModelSelectorTool autoModelSelectorTool;

    @Autowired(required = false)
    private VectorMemoryService memoryService;

    @Autowired(required = false)
    private AgentManager agentManager;

    private boolean autoModelSelectionEnabled = true; // Default enabled

    private final List<ConversationMessage> conversationHistory = new ArrayList<>();
    private static final int MAX_TOOL_ITERATIONS = 10;
    private String sessionContext = null;
    private String currentModelInfo = null; // Store current model being used

    public String processMessage(final String userMessage) {
        // Add user message to history
        final ConversationMessage userMsg = ConversationMessage.builder()
                .role("user")
                .content(userMessage)
                .timestamp(LocalDateTime.now())
                .build();
        conversationHistory.add(userMsg);

        try {
            // Get the appropriate chat client based on automatic model selection
            final ChatClient chatClient = selectChatClient(userMessage);

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
            final String finalResponse = runConversationLoop(chatClient, contextPrefix + userMessage);

            // Add assistant response to history
            final String currentAgent = agentManager != null ? agentManager.getCurrentAgentName() : null;
            final ConversationMessage assistantMsg = ConversationMessage.builder()
                    .role("assistant")
                    .content(finalResponse)
                    .agentName(currentAgent)
                    .timestamp(LocalDateTime.now())
                    .build();
            conversationHistory.add(assistantMsg);

            // Store in vector memory if available
            if (memoryService != null) {
                final AgentConfiguration config = configManager.getConfiguration();
                memoryService.storePrompt(
                        userMessage,
                        finalResponse,
                        config.getDefaultProvider().toString(),
                        config.getLlmConfigs().get(config.getDefaultProvider()).getModel()
                        );
            }

            return finalResponse;

        } catch (final Exception e) {
            log.error("Error processing message", e);
            return "Error: " + e.getMessage();
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

        // If auto-selection is disabled, use default provider
        if (!autoModelSelectionEnabled) {
            log.debug("Auto model selection disabled, using default provider");
            return llmClientFactory.getChatClient(
                    configManager.getConfiguration().getDefaultProvider()
            );
        }

        // Try to detect task type from prompt
        final Optional<TaskType> detectedTaskType = autoModelSelectorTool.selectTaskTypeForPrompt(userMessage);

        if (detectedTaskType.isEmpty()) {
            log.debug("No specific task type detected, using default provider");
            return llmClientFactory.getChatClient(
                    configManager.getConfiguration().getDefaultProvider()
            );
        }

        // Check if task-specific model is configured
        final TaskType taskType = detectedTaskType.get();
        final AgentConfiguration config = configManager.getConfiguration();
        final AgentConfiguration.TaskModelConfig taskConfig =
                config.getTaskModels().get(taskType);

        if (taskConfig == null) {
            log.debug("Task type {} detected but no task-specific model configured, using default", taskType);
            return llmClientFactory.getChatClient(config.getDefaultProvider());
        }

        // Use task-specific model
        log.info("🎯 Auto-selected model for {}: {} - {}",
                taskType.getDisplayName(),
                taskConfig.getProvider(),
                taskConfig.getModel());

        // Store model info for display (without printing it here - prevents it from appearing in input field)
        currentModelInfo = String.format("\033[90m[%s: %s/%s]\033[0m",
                taskType.getDisplayName(),
                taskConfig.getProvider(),
                taskConfig.getModel());

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

        // Replace the last user message with the enhanced one (with context)
        if (!messages.isEmpty() && messages.get(messages.size() - 1) instanceof UserMessage) {
            messages.set(messages.size() - 1, new UserMessage(enhancedUserMessage));
        }
        int iteration = 0;

        // Tools are now registered as default tools in LLMClientFactory
        // No need to pass them with each request
        log.debug("Using default tools registered with ChatClient");

        while (iteration < MAX_TOOL_ITERATIONS) {
            iteration++;

            // Build prompt - tools are automatically available from ChatClient defaults
            final Prompt prompt = new Prompt(messages);

            try {
                // Call LLM with tools enabled
                final ChatResponse response = chatClient.prompt(prompt)
                        .call()
                        .chatResponse();

                final AssistantMessage assistantMessage = response.getResult().getOutput();
                messages.add(assistantMessage);

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

                        final String result = tool.execute(argsMap);
                        formatter.printToolCall(toolName, result);

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

        // Load session context (USER.md and CHARACTER.md) on first message
        if (sessionContext == null) {
            // Get current agent name if available
            final String currentAgent = agentManager != null ? agentManager.getCurrentAgentName() : null;
            if (currentAgent != null) {
                // Load agent-specific CHARACTER.md
                sessionContext = sessionContextManager.buildInitialContext(currentAgent);
            } else {
                // Fallback to loading only USER.md
                sessionContext = sessionContextManager.buildInitialContext();
            }
        }

        // Build concise system message with session context
        final StringBuilder systemMessageBuilder = new StringBuilder();

        // Add extracted character profile (concise)
        if (!sessionContext.isEmpty()) {
            systemMessageBuilder.append(sessionContext);
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

        // Convert conversation history
        for (final ConversationMessage msg : conversationHistory) {
            if ("user".equals(msg.getRole())) {
                messages.add(new UserMessage(msg.getContent()));
            } else if ("assistant".equals(msg.getRole())) {
                messages.add(new AssistantMessage(msg.getContent()));
            }
        }

        return messages;
    }


    public void clearHistory() {
        conversationHistory.clear();
        formatter.printSuccess("Conversation history cleared");
    }

    public void reloadSessionContext() {
        sessionContext = sessionContextManager.buildInitialContext();
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
        sessionContext = sessionContextManager.buildInitialContext(agentName);
        log.info("🔄 Session context reloaded for agent '{}' from USER.md and CHARACTER.md", agentName);
        formatter.printSuccess("Session context reloaded for agent '" + agentName + "'");
    }

    public List<ConversationMessage> getConversationHistory() {
        return new ArrayList<>(conversationHistory);
    }
}

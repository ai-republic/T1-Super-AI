package com.airepublic.t1.agent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import com.airepublic.t1.config.AgentConfigurationManager;
import com.airepublic.t1.model.AgentConfiguration;
import com.airepublic.t1.model.AgentConfiguration.LLMConfig;
import com.airepublic.t1.model.AgentConfiguration.LLMProvider;
import com.airepublic.t1.model.AgentConfiguration.TaskModelConfig;
import com.airepublic.t1.model.AgentConfiguration.TaskType;
import com.airepublic.t1.tools.AgentTool;

import lombok.extern.slf4j.Slf4j;
import reactor.netty.http.client.HttpClient;

/**
 * Factory for creating LLM chat clients based on user configuration.
 *
 * This factory creates chat models MANUALLY after reading configuration from
 * ~/.t1-super-ai/config.json. Spring AI auto-configuration is disabled.
 *
 * Models are created lazily and cached for reuse.
 * Tools are dynamically registered with each ChatClient.
 */
@Slf4j
@Component
public class LLMClientFactory {
    private final AgentConfigurationManager configManager;
    private final ToolRegistry toolRegistry;

    private final Map<LLMProvider, ChatClient> chatClients = new ConcurrentHashMap<>();
    private final Map<String, ChatClient> taskBasedChatClients = new ConcurrentHashMap<>();

    public LLMClientFactory(final AgentConfigurationManager configManager, final ToolRegistry toolRegistry) {
        this.configManager = configManager;
        this.toolRegistry = toolRegistry;
    }

    /**
     * Gets or creates a chat client for the specified task type.
     * The client is created based on task-specific provider/model configuration.
     *
     * @param taskType The type of task (GENERAL_KNOWLEDGE, CODING, SPEECH, VIDEO)
     * @return ChatClient configured for the specific task
     * @throws IllegalStateException if no configuration found for task
     */
    public ChatClient getChatClientForTask(final TaskType taskType) {
        final AgentConfiguration config = configManager.getConfiguration();
        final TaskModelConfig taskConfig = config.getTaskModels().get(taskType);

        if (taskConfig == null || taskConfig.getProvider() == null) {
            log.warn("No task-specific configuration for {}, falling back to default provider", taskType);
            return getChatClient(config.getDefaultProvider());
        }

        // Create a unique key for this task configuration
        final String cacheKey = taskType.name() + "_" + taskConfig.getProvider() + "_" + taskConfig.getModel();
        return taskBasedChatClients.computeIfAbsent(cacheKey, k -> createTaskBasedChatClient(taskConfig));
    }

    /**
     * Gets or creates a chat client for the specified provider.
     * The client is created based on user configuration from config.json.
     *
     * @param provider The LLM provider (OPENAI, ANTHROPIC, OLLAMA)
     * @return ChatClient configured with user's settings
     * @throws IllegalStateException if no configuration found for provider
     */
    public ChatClient getChatClient(final LLMProvider provider) {
        return chatClients.computeIfAbsent(provider, this::createChatClient);
    }

    /**
     * Creates a chat client for a specific task configuration.
     */
    private ChatClient createTaskBasedChatClient(final TaskModelConfig taskConfig) {
        final AgentConfiguration config = configManager.getConfiguration();
        final LLMConfig llmConfig = config.getLlmConfigs().get(taskConfig.getProvider());

        if (llmConfig == null) {
            throw new IllegalStateException("No configuration found for provider: " + taskConfig.getProvider());
        }

        log.info("Creating task-specific {} chat client with model: {}",
                taskConfig.getProvider(), taskConfig.getModel());

        // Override the model from task configuration
        final LLMConfig taskSpecificConfig = new LLMConfig();
        taskSpecificConfig.setApiKey(llmConfig.getApiKey());
        taskSpecificConfig.setBaseUrl(llmConfig.getBaseUrl());
        taskSpecificConfig.setModel(taskConfig.getModel());
        taskSpecificConfig.setAdditionalParams(llmConfig.getAdditionalParams());

        return switch (taskConfig.getProvider()) {
            case OPENAI -> createOpenAiChatClient(taskSpecificConfig, config);
            case ANTHROPIC -> createAnthropicChatClient(taskSpecificConfig, config);
            case OLLAMA -> createOllamaChatClient(taskSpecificConfig, config);
        };
    }

    private ChatClient createChatClient(final LLMProvider provider) {
        final AgentConfiguration config = configManager.getConfiguration();
        final LLMConfig llmConfig = config.getLlmConfigs().get(provider);

        if (llmConfig == null) {
            throw new IllegalStateException("No configuration found for provider: " + provider);
        }

        log.info("Creating {} chat client with model: {}", provider, llmConfig.getModel());

        return switch (provider) {
            case OPENAI -> createOpenAiChatClient(llmConfig, config);
            case ANTHROPIC -> createAnthropicChatClient(llmConfig, config);
            case OLLAMA -> createOllamaChatClient(llmConfig, config);
        };
    }

    private ChatClient createOpenAiChatClient(final LLMConfig llmConfig, final AgentConfiguration config) {
        try {
            log.debug("Creating OpenAI client with model: {}", llmConfig.getModel());

            // Create RestClient with increased timeout
            final RestClient.Builder restClientBuilder = RestClient.builder()
                    .requestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory(
                            java.net.http.HttpClient.newBuilder()
                            .connectTimeout(java.time.Duration.ofSeconds(30))
                            .build()
                            ));

            final OpenAiApi api = OpenAiApi.builder()
                    .apiKey(llmConfig.getApiKey())
                    .restClientBuilder(restClientBuilder)
                    .build();

            // Create the chat model with increased timeout
            final OpenAiChatModel chatModel = OpenAiChatModel.builder()
                    .openAiApi(api)
                    .defaultOptions(OpenAiChatOptions.builder()
                            .model(llmConfig.getModel())
                            .build())
                    .build();

            // Build ChatClient with tools
            final ChatClient.Builder builder = ChatClient.builder(chatModel);

            // Register all tools with the ChatClient
            registerToolsWithClient(builder);

            return builder.build();

        } catch (final Exception e) {
            log.error("Error creating OpenAI chat client", e);
            throw new RuntimeException("Failed to create OpenAI chat client: " + e.getMessage(), e);
        }
    }

    private ChatClient createAnthropicChatClient(final LLMConfig llmConfig, final AgentConfiguration config) {
        try {
            log.debug("Creating Anthropic client with model: {}", llmConfig.getModel());

            // Create RestClient with increased timeout
            final RestClient.Builder restClientBuilder = RestClient.builder()
                    .requestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory(
                            java.net.http.HttpClient.newBuilder()
                            .connectTimeout(java.time.Duration.ofSeconds(30))
                            .build()
                            ));

            final AnthropicApi api = AnthropicApi.builder()
                    .apiKey(llmConfig.getApiKey())
                    .restClientBuilder(restClientBuilder)
                    .build();

            // Create the chat model
            final AnthropicChatModel chatModel = AnthropicChatModel.builder()
                    .anthropicApi(api)
                    .defaultOptions(AnthropicChatOptions.builder()
                            .model(llmConfig.getModel())
                            .build())
                    .build();

            // Build ChatClient with tools
            final ChatClient.Builder builder = ChatClient.builder(chatModel);

            // Register all tools with the ChatClient
            registerToolsWithClient(builder);

            return builder.build();

        } catch (final Exception e) {
            log.error("Error creating Anthropic chat client", e);
            throw new RuntimeException("Failed to create Anthropic chat client: " + e.getMessage(), e);
        }
    }

    private ChatClient createOllamaChatClient(final LLMConfig llmConfig, final AgentConfiguration config) {
        try {
            // Configure HTTP client with generous timeouts for Ollama
            final HttpClient httpClient = HttpClient.create()
                    .responseTimeout(Duration.ofMinutes(10))  // 10 minutes read timeout
                    .option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, 300000); // 5min connect

            // Create WebClient builder with timeout configuration
            final WebClient.Builder webClientBuilder = WebClient.builder()
                    .clientConnector(new ReactorClientHttpConnector(httpClient))
                    .codecs(configurer -> configurer
                            .defaultCodecs()
                            .maxInMemorySize(10 * 1024 * 1024)); // 10MB buffer

            // Create RestClient builder with increased timeout
            final RestClient.Builder restClientBuilder = RestClient.builder()
                    .requestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory(
                            java.net.http.HttpClient.newBuilder()
                            .connectTimeout(Duration.ofMinutes(5))
                            .build()
                            ));

            // Create Ollama API using builder pattern (Spring AI 2.0)
            final OllamaApi ollamaApi = OllamaApi.builder()
                    .baseUrl(llmConfig.getBaseUrl() != null ? llmConfig.getBaseUrl() : "http://localhost:11434")
                    .restClientBuilder(restClientBuilder)
                    .webClientBuilder(webClientBuilder)
                    .build();

            // Create chat options
            final OllamaChatOptions options = OllamaChatOptions.builder()
                    .model(llmConfig.getModel())
                    .temperature(config.getSystemSettings().getTemperature())
                    .build();

            // Create chat model
            final OllamaChatModel chatModel = OllamaChatModel.builder()
                    .ollamaApi(ollamaApi)
                    .defaultOptions(options)
                    .build();

            // Build ChatClient with tools
            final ChatClient.Builder builder = ChatClient.builder(chatModel);

            // Register all tools with the ChatClient
            registerToolsWithClient(builder);

            return builder.build();

        } catch (final Exception e) {
            log.error("Error creating Ollama chat client", e);
            throw new RuntimeException("Failed to create Ollama chat client: " + e.getMessage(), e);
        }
    }

    /**
     * Registers all tools from ToolRegistry with the ChatClient builder as default tools.
     * This ensures the LLM has access to all tools (core tools + plugins + MCP tools)
     * for every request without needing to pass them explicitly.
     *
     * @param builder The ChatClient.Builder
     */
    private void registerToolsWithClient(final ChatClient.Builder builder) {
        final Collection<AgentTool> allTools = toolRegistry.getAllTools();

        if (allTools.isEmpty()) {
            log.warn("No tools available to register with ChatClient");
            return;
        }

        // Create FunctionToolCallback instances for each AgentTool
        final List<ToolCallback> toolCallbacks = new ArrayList<>();

        for (final AgentTool tool : allTools) {
            log.debug("Registering default tool: {} - {}", tool.getName(), tool.getDescription());

            try {
                // Create FunctionToolCallback that wraps the AgentTool
                final ToolCallback toolCallback = FunctionToolCallback
                        .<Map<String, Object>, String>builder(tool.getName(), (final Map<String, Object> args) -> {
                            try {
                                return tool.execute(args);
                            } catch (final Exception e) {
                                log.error("Error executing tool: {}", tool.getName(), e);
                                return "Error: " + e.getMessage();
                            }
                        })
                        .description(tool.getDescription())
                        .inputType(new ParameterizedTypeReference<Map<String, Object>>() {})
                        .inputSchema(convertToJsonSchema(tool.getParameterSchema()))
                        .build();

                toolCallbacks.add(toolCallback);
            } catch (final Exception e) {
                log.error("Failed to register tool: {}", tool.getName(), e);
            }
        }

        // Register all tool callbacks as default tools with the ChatClient
        if (!toolCallbacks.isEmpty()) {
            builder.defaultToolCallbacks(toolCallbacks);
            log.info("✅ Registered {} default tools with ChatClient", toolCallbacks.size());
        }
    }

    /**
     * Converts a parameter schema Map to JSON schema string for ToolCallback.
     */
    private String convertToJsonSchema(final Map<String, Object> schema) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(schema);
        } catch (final Exception e) {
            log.error("Error converting schema to JSON", e);
            return "{}";
        }
    }

    /**
     * Clears the chat client cache, forcing recreation on next access.
     * Useful when configuration changes or when tools are updated.
     */
    public void clearCache() {
        chatClients.clear();
        taskBasedChatClients.clear();
        log.info("Chat client cache cleared - will re-register tools on next access");
    }
}

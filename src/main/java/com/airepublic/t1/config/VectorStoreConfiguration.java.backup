package com.airepublic.t1.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Configuration for airepublic VectorStore with Spring AI integration.
 * <p>
 * This configuration uses the built-in Spring AI VectorStore adapter from the
 * airepublic vectorstore library, which provides seamless integration with Spring AI 2.0.
 * <p>
 * The library's auto-configuration creates SpringAIVectorStore instances per agent.
 * Vector data is stored in agent-specific folders.
 * <p>
 * Enable by setting: spring.ai.vectorstore.enabled=true
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "spring.ai.vectorstore", name = "enabled", havingValue = "true")
public class VectorStoreConfiguration {

    /**
     * Create the EmbeddingModel bean based on agent configuration.
     * Uses the default provider from agent configuration.
     */
    @Bean
    public EmbeddingModel embeddingModel(AgentConfigurationManager configManager) {
        var config = configManager.getConfiguration();
        var defaultProvider = config.getDefaultProvider();

        log.info("Initializing EmbeddingModel with provider: {}", defaultProvider);

        return switch (defaultProvider) {
            case OPENAI -> createOpenAiEmbeddingModel(configManager);
            case OLLAMA -> createOllamaEmbeddingModel(configManager);
            case ANTHROPIC -> {
                log.warn("Anthropic does not provide embedding models, falling back to OpenAI");
                yield createOpenAiEmbeddingModel(configManager);
            }
        };
    }

    private EmbeddingModel createOpenAiEmbeddingModel(AgentConfigurationManager configManager) {
        var config = configManager.getConfiguration();
        var openAiConfig = config.getLlmConfigs().get(com.airepublic.t1.model.AgentConfiguration.LLMProvider.OPENAI);

        if (openAiConfig == null || openAiConfig.getApiKey() == null) {
            throw new IllegalStateException("OpenAI configuration not found. Please configure OpenAI in ~/.t1-super-ai/config.json");
        }

        RestClient.Builder restClientBuilder = RestClient.builder()
            .requestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory(
                java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(30))
                    .build()
            ));

        OpenAiApi api = OpenAiApi.builder()
            .apiKey(openAiConfig.getApiKey())
            .restClientBuilder(restClientBuilder)
            .build();

        // Use text-embedding-3-small (dimension 1536) as default
        String embeddingModel = "text-embedding-3-small";
        log.info("Created OpenAI EmbeddingModel with model: {}", embeddingModel);
        return new OpenAiEmbeddingModel(
            api,
            org.springframework.ai.document.MetadataMode.ALL,
            org.springframework.ai.openai.OpenAiEmbeddingOptions.builder()
                .model(embeddingModel)
                .build()
        );
    }

    private EmbeddingModel createOllamaEmbeddingModel(AgentConfigurationManager configManager) {
        var config = configManager.getConfiguration();
        var ollamaConfig = config.getLlmConfigs().get(com.airepublic.t1.model.AgentConfiguration.LLMProvider.OLLAMA);

        String baseUrl = ollamaConfig != null && ollamaConfig.getBaseUrl() != null
            ? ollamaConfig.getBaseUrl()
            : "http://localhost:11434";

        RestClient.Builder restClientBuilder = RestClient.builder()
            .requestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory(
                java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(30))
                    .build()
            ));

        OllamaApi api = OllamaApi.builder()
            .baseUrl(baseUrl)
            .restClientBuilder(restClientBuilder)
            .build();

        // Use nomic-embed-text (dimension 768) as default for Ollama
        String embeddingModel = "nomic-embed-text";
        log.info("Created Ollama EmbeddingModel with model: {} at {}", embeddingModel, baseUrl);
        return OllamaEmbeddingModel.builder()
            .ollamaApi(api)
            .defaultOptions(org.springframework.ai.ollama.api.OllamaEmbeddingOptions.builder()
                .model(embeddingModel)
                .build())
            .build();
    }
}

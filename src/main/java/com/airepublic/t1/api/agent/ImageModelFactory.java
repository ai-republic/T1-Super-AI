package com.airepublic.t1.agent;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.ai.image.ImageModel;
import org.springframework.ai.openai.OpenAiImageModel;
import org.springframework.ai.openai.OpenAiImageOptions;
import org.springframework.ai.openai.api.OpenAiImageApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.airepublic.t1.config.AgentConfigurationManager;
import com.airepublic.t1.model.AgentConfiguration;
import com.airepublic.t1.model.AgentConfiguration.LLMConfig;
import com.airepublic.t1.model.AgentConfiguration.LLMProvider;
import com.airepublic.t1.model.AgentConfiguration.TaskModelConfig;
import com.airepublic.t1.model.AgentConfiguration.TaskType;

import lombok.extern.slf4j.Slf4j;

/**
 * Factory for creating ImageModel clients for image generation tasks.
 *
 * Currently supports:
 * - OpenAI DALL-E models (dall-e-2, dall-e-3)
 *
 * Future support planned for:
 * - Stability AI
 * - Other image generation providers
 */
@Slf4j
@Component
public class ImageModelFactory {
    private final AgentConfigurationManager configManager;
    private final Map<String, ImageModel> imageModels = new ConcurrentHashMap<>();

    public ImageModelFactory(final AgentConfigurationManager configManager) {
        this.configManager = configManager;
        // Register this factory with the config manager for cache clearing
        configManager.setImageModelFactory(this);
    }

    /**
     * Gets or creates an ImageModel for the IMAGE_GENERATION task type.
     * Falls back to GENERAL_KNOWLEDGE if IMAGE_GENERATION is not configured and
     * GENERAL_KNOWLEDGE uses OpenAI.
     *
     * @return ImageModel configured for image generation
     * @throws IllegalStateException if no configuration found or provider not supported
     */
    public ImageModel getImageModelForTask() {
        final AgentConfiguration config = configManager.getConfiguration();
        final TaskModelConfig taskConfig = config.getTaskModels().get(TaskType.IMAGE_GENERATION);

        if (taskConfig == null || taskConfig.getProvider() == null) {
            log.warn("No task-specific configuration for IMAGE_GENERATION, checking GENERAL_KNOWLEDGE fallback");
            // Try to use GENERAL_KNOWLEDGE if it's OpenAI
            final TaskModelConfig fallbackConfig = config.getTaskModels().get(TaskType.GENERAL_KNOWLEDGE);
            if (fallbackConfig != null && fallbackConfig.getProvider() == LLMProvider.OPENAI) {
                final LLMConfig llmConfig = config.getLlmConfigs().get(LLMProvider.OPENAI);
                if (llmConfig != null) {
                    final String cacheKey = "OPENAI_FALLBACK";
                    return imageModels.computeIfAbsent(cacheKey, k -> createOpenAiImageModel(llmConfig));
                }
            }
            throw new IllegalStateException(
                "No IMAGE_GENERATION task model configured. " +
                "Please configure an OpenAI model (dall-e-3) for image generation"
            );
        }

        // Validate provider supports image generation
        if (taskConfig.getProvider() != LLMProvider.OPENAI) {
            throw new IllegalStateException(
                String.format("Provider %s does not support image generation. " +
                    "Only OPENAI (DALL-E) is currently supported.",
                    taskConfig.getProvider())
            );
        }

        // Create a unique key for this task configuration
        final String cacheKey = taskConfig.getProvider() + "_" + taskConfig.getModel();
        return imageModels.computeIfAbsent(cacheKey, k -> {
            final LLMConfig llmConfig = config.getLlmConfigs().get(taskConfig.getProvider());
            if (llmConfig == null) {
                throw new IllegalStateException(
                    "No configuration found for provider: " + taskConfig.getProvider()
                );
            }
            // Use the task-specific model and API key (with fallback to provider's general API key)
            final LLMConfig taskSpecificConfig = new LLMConfig();

            // Use task-specific API key if provided, otherwise fall back to provider's general API key
            final String apiKey = (taskConfig.getApiKey() != null && !taskConfig.getApiKey().isEmpty())
                ? taskConfig.getApiKey()
                : llmConfig.getApiKey();

            taskSpecificConfig.setApiKey(apiKey);
            taskSpecificConfig.setBaseUrl(llmConfig.getBaseUrl());
            taskSpecificConfig.setModel(taskConfig.getModel());

            if (taskConfig.getApiKey() != null && !taskConfig.getApiKey().isEmpty()) {
                log.debug("Using task-specific API key for IMAGE_GENERATION");
            } else {
                log.debug("Using provider's general API key for IMAGE_GENERATION");
            }

            return createOpenAiImageModel(taskSpecificConfig);
        });
    }

    /**
     * Creates an OpenAI ImageModel client using Spring AI's OpenAiImageModel.
     *
     * @param llmConfig The LLM configuration with API key and model
     * @return OpenAiImageModel configured for DALL-E image generation
     */
    private ImageModel createOpenAiImageModel(final LLMConfig llmConfig) {
        try {
            log.info("Creating OpenAI ImageModel with model: {}", llmConfig.getModel());

            // Create RestClient with increased timeout
            final RestClient.Builder restClientBuilder = RestClient.builder()
                    .requestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory(
                            java.net.http.HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(60))
                            .build()
                    ));

            // Create OpenAI Image API
            final OpenAiImageApi imageApi = OpenAiImageApi.builder()
                    .apiKey(llmConfig.getApiKey())
                    .restClientBuilder(restClientBuilder)
                    .build();

            // Create default options
            // Note: gpt-image-1.5 uses different parameters than DALL-E:
            // - size should be "1024x1024", "1536x1024", "1024x1536", or "auto"
            // - quality can be "low", "medium", or "high"
            // - gpt-image models always return base64-encoded images (no responseFormat parameter)
            final OpenAiImageOptions.Builder optionsBuilder = OpenAiImageOptions.builder()
                    .model(llmConfig.getModel() != null ? llmConfig.getModel() : "dall-e-3")
                    .quality("medium") // for gpt-image: "low", "medium", or "high"
                    .N(1); // number of images to generate

            // Only set responseFormat for DALL-E models, not for gpt-image models
            // gpt-image models don't support responseFormat parameter and always return base64
            if (llmConfig.getModel() != null && llmConfig.getModel().startsWith("dall-e")) {
                optionsBuilder.responseFormat("url");
                optionsBuilder.width(1024).height(1024);
            }

            final OpenAiImageOptions defaultOptions = optionsBuilder.build();

            // Set size for gpt-image models (not supported by DALL-E)
            if (llmConfig.getModel() != null && llmConfig.getModel().startsWith("gpt-image")) {
                defaultOptions.setSize("1024x1024");
            }

            // Create and return the ImageModel with RetryUtils.DEFAULT_RETRY_TEMPLATE
            final OpenAiImageModel imageModel = new OpenAiImageModel(imageApi, defaultOptions, RetryUtils.DEFAULT_RETRY_TEMPLATE);

            log.info("✅ OpenAI ImageModel created successfully with model: {}", llmConfig.getModel());
            return imageModel;

        } catch (final Exception e) {
            log.error("Error creating OpenAI ImageModel", e);
            throw new RuntimeException("Failed to create OpenAI ImageModel: " + e.getMessage(), e);
        }
    }

    /**
     * Clears the image model cache, forcing recreation on next access.
     */
    public void clearCache() {
        imageModels.clear();
        log.info("Image model cache cleared");
    }
}

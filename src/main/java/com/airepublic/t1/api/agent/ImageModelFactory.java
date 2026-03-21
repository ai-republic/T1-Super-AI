package com.airepublic.t1.agent;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.ai.image.ImageModel;
import org.springframework.ai.openai.OpenAiImageModel;
import org.springframework.ai.openai.OpenAiImageOptions;
import org.springframework.ai.openai.api.OpenAiImageApi;
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
    }

    /**
     * Gets or creates an ImageModel for the IMAGE_GENERATION task type.
     *
     * @return ImageModel configured for image generation
     * @throws IllegalStateException if no configuration found or provider not supported
     */
    public ImageModel getImageModelForTask() {
        final AgentConfiguration config = configManager.getConfiguration();
        final TaskModelConfig taskConfig = config.getTaskModels().get(TaskType.IMAGE_GENERATION);

        if (taskConfig == null || taskConfig.getProvider() == null) {
            log.warn("No task-specific configuration for IMAGE_GENERATION, checking default provider");
            // Try to use default provider if it's OpenAI
            if (config.getDefaultProvider() == LLMProvider.OPENAI) {
                final LLMConfig llmConfig = config.getLlmConfigs().get(LLMProvider.OPENAI);
                if (llmConfig != null) {
                    final String cacheKey = "OPENAI_DEFAULT";
                    return imageModels.computeIfAbsent(cacheKey, k -> createOpenAiImageModel(llmConfig));
                }
            }
            throw new IllegalStateException(
                "No IMAGE_GENERATION task model configured. " +
                "Please configure an OpenAI model (dall-e-3) for image generation using /task-model config"
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
            // Use the task-specific model
            final LLMConfig taskSpecificConfig = new LLMConfig();
            taskSpecificConfig.setApiKey(llmConfig.getApiKey());
            taskSpecificConfig.setBaseUrl(llmConfig.getBaseUrl());
            taskSpecificConfig.setModel(taskConfig.getModel());

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
            final OpenAiImageOptions defaultOptions = OpenAiImageOptions.builder()
                    .model(llmConfig.getModel() != null ? llmConfig.getModel() : "dall-e-3")
                    .quality("standard") // can be "standard" or "hd"
                    .N(1) // number of images to generate
                    .width(1024)
                    .height(1024)
                    .responseFormat("url") // can be "url" or "b64_json"
                    .build();

            // Create and return the ImageModel
            final OpenAiImageModel imageModel = new OpenAiImageModel(imageApi, defaultOptions, null);

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

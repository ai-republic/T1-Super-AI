package com.airepublic.t1.tools;

import com.airepublic.t1.agent.ImageModelFactory;
import com.airepublic.t1.agent.LLMClientFactory;
import com.airepublic.t1.config.AgentConfigurationManager;
import com.airepublic.t1.model.AgentConfiguration;
import com.airepublic.t1.model.AgentConfiguration.TaskModelConfig;
import com.airepublic.t1.model.AgentConfiguration.TaskType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Tool that allows agents to execute prompts using task-specific models.
 *
 * This tool enables agents to:
 * - Execute a prompt with a specific task model (e.g., use Whisper for speech-to-text)
 * - Get responses from specialized models for specific task types
 * - Leverage the power of task-optimized models without switching the main model
 *
 * Example use cases:
 * - Agent needs to analyze an image: use IMAGE_ANALYSIS model
 * - Agent needs to generate code: use CODING model
 * - Agent needs to transcribe audio: use SPEECH_TO_TEXT model
 */
@Component
@Slf4j
public class ExecuteWithTaskModelTool implements AgentTool {

    private final LLMClientFactory clientFactory;
    private final ImageModelFactory imageModelFactory;
    private final AgentConfigurationManager configManager;

    public ExecuteWithTaskModelTool(
            final LLMClientFactory clientFactory,
            final ImageModelFactory imageModelFactory,
            final AgentConfigurationManager configManager) {
        this.clientFactory = clientFactory;
        this.imageModelFactory = imageModelFactory;
        this.configManager = configManager;
    }

    @Override
    public String getName() {
        return "execute_with_task_model";
    }

    @Override
    public String getDescription() {
        return "Executes a prompt using a task-specific AI model. " +
               "Use this when you need a specialized model for a particular task type. " +
               "Available task types: " +
               "GENERAL_KNOWLEDGE (general Q&A), " +
               "CODING (code generation/debugging), " +
               "SPEECH_TO_TEXT (audio transcription), " +
               "TEXT_TO_SPEECH (text to audio), " +
               "IMAGE_ANALYSIS (image understanding), " +
               "IMAGE_GENERATION (create images), " +
               "VIDEO_ANALYSIS (video understanding), " +
               "VIDEO_GENERATION (create/edit videos). " +
               "Parameters: task_type (string) - The task type to use, " +
               "prompt (string) - The prompt to execute with the task-specific model.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new HashMap<>();

        // task_type parameter
        Map<String, Object> taskTypeParam = new HashMap<>();
        taskTypeParam.put("type", "string");
        taskTypeParam.put("description", "The task type to use for model selection");
        taskTypeParam.put("enum", new String[]{
            "GENERAL_KNOWLEDGE", "CODING", "SPEECH_TO_TEXT", "TEXT_TO_SPEECH",
            "IMAGE_ANALYSIS", "IMAGE_GENERATION", "VIDEO_ANALYSIS", "VIDEO_GENERATION"
        });
        properties.put("task_type", taskTypeParam);

        // prompt parameter
        Map<String, Object> promptParam = new HashMap<>();
        promptParam.put("type", "string");
        promptParam.put("description", "The prompt to execute with the task-specific model");
        properties.put("prompt", promptParam);

        schema.put("properties", properties);
        schema.put("required", new String[]{"task_type", "prompt"});

        return schema;
    }

    @Override
    public String execute(Map<String, Object> arguments) throws Exception {
        String taskTypeStr = (String) arguments.get("task_type");
        String prompt = (String) arguments.get("prompt");

        if (taskTypeStr == null || taskTypeStr.trim().isEmpty()) {
            log.error("Task type not provided");
            return "Error: task_type parameter is required. " +
                   "Available: GENERAL_KNOWLEDGE, CODING, SPEECH_TO_TEXT, TEXT_TO_SPEECH, " +
                   "IMAGE_ANALYSIS, IMAGE_GENERATION, VIDEO_ANALYSIS, VIDEO_GENERATION";
        }

        if (prompt == null || prompt.trim().isEmpty()) {
            log.error("Prompt not provided");
            return "Error: prompt parameter is required";
        }

        try {
            // Parse task type
            TaskType taskType = TaskType.valueOf(taskTypeStr.toUpperCase());

            // Get configuration
            AgentConfiguration config = configManager.getConfiguration();
            TaskModelConfig taskConfig = config.getTaskModels().get(taskType);

            if (taskConfig == null) {
                log.warn("No task-specific model configured for {}, using default provider", taskType);
                return String.format(
                    "Warning: No task-specific model configured for %s. " +
                    "Please configure a model for this task type using /task-model config. " +
                    "Falling back to default provider: %s",
                    taskType.getDisplayName(),
                    config.getDefaultProvider()
                );
            }

            log.info("Executing prompt with task-specific model: {} - {} / {}",
                    taskType, taskConfig.getProvider(), taskConfig.getModel());

            // Handle IMAGE_GENERATION specially with ImageModel
            if (taskType == TaskType.IMAGE_GENERATION) {
                return executeImageGeneration(prompt, taskConfig);
            }

            // For all other task types, use ChatClient
            ChatClient chatClient = clientFactory.getChatClientForTask(taskType);

            // Execute the prompt
            ChatResponse response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .chatResponse();

            String result = response.getResult().getOutput().getText();

            log.info("Task-specific model execution completed for {}", taskType);

            return String.format(
                "✅ Executed with %s model (%s - %s):\n\n%s",
                taskType.getDisplayName(),
                taskConfig.getProvider(),
                taskConfig.getModel(),
                result
            );

        } catch (IllegalArgumentException e) {
            log.error("Invalid task type: {}", taskTypeStr);
            return "Error: Invalid task_type. " +
                   "Available: GENERAL_KNOWLEDGE, CODING, SPEECH_TO_TEXT, TEXT_TO_SPEECH, " +
                   "IMAGE_ANALYSIS, IMAGE_GENERATION, VIDEO_ANALYSIS, VIDEO_GENERATION";
        } catch (Exception e) {
            log.error("Error executing with task-specific model", e);
            return "Error executing with task-specific model: " + e.getMessage();
        }
    }

    /**
     * Executes image generation using Spring AI's ImageModel (OpenAI DALL-E).
     *
     * @param prompt The image generation prompt
     * @param taskConfig The task configuration
     * @return A formatted response with the generated image URL
     */
    private String executeImageGeneration(final String prompt, final TaskModelConfig taskConfig) {
        try {
            log.info("Executing image generation with OpenAI DALL-E");

            // Get the ImageModel for image generation
            final ImageModel imageModel = imageModelFactory.getImageModelForTask();

            // Create an ImagePrompt with the user's prompt
            final ImagePrompt imagePrompt = new ImagePrompt(prompt);

            // Call the ImageModel to generate the image
            final ImageResponse imageResponse = imageModel.call(imagePrompt);

            // Extract the image URL from the response
            final String imageUrl = imageResponse.getResult().getOutput().getUrl();

            log.info("Image generation completed successfully. URL: {}", imageUrl);

            return String.format(
                "✅ Image generated with %s model (%s - %s):\n\n" +
                "Image URL: %s\n\n" +
                "💡 You can view or download the image from the URL above. " +
                "The image will be available for a limited time.",
                TaskType.IMAGE_GENERATION.getDisplayName(),
                taskConfig.getProvider(),
                taskConfig.getModel(),
                imageUrl
            );

        } catch (final Exception e) {
            log.error("Error generating image", e);
            return String.format(
                "❌ Error generating image: %s\n\n" +
                "💡 Make sure you have configured an OpenAI API key and selected a DALL-E model " +
                "(dall-e-2 or dall-e-3) for IMAGE_GENERATION task type.",
                e.getMessage()
            );
        }
    }
}

package com.airepublic.t1.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
public class AgentConfiguration {
    private Map<LLMProvider, LLMConfig> llmConfigs = new HashMap<>();

    // Task-based provider/model configuration
    // GENERAL_KNOWLEDGE is mandatory and serves as the fallback for all tasks
    private Map<TaskType, TaskModelConfig> taskModels = new HashMap<>();

    private List<MCPServerConfig> mcpServers = new ArrayList<>();
    private SystemSettings systemSettings = new SystemSettings();

    @Data
    public static class LLMConfig {
        private String apiKey;
        private String baseUrl;
        private String model;
        private Map<String, Object> additionalParams = new HashMap<>();
    }

    @Data
    public static class TaskModelConfig {
        private LLMProvider provider;
        private String model;
        private String apiKey; // Optional: if not specified, uses provider's general API key
        private String baseUrl; // Optional: if not specified, uses provider's default base URL

        public TaskModelConfig() {
        }

        public TaskModelConfig(LLMProvider provider, String model) {
            this.provider = provider;
            this.model = model;
        }

        public TaskModelConfig(LLMProvider provider, String model, String apiKey) {
            this.provider = provider;
            this.model = model;
            this.apiKey = apiKey;
        }

        public TaskModelConfig(LLMProvider provider, String model, String apiKey, String baseUrl) {
            this.provider = provider;
            this.model = model;
            this.apiKey = apiKey;
            this.baseUrl = baseUrl;
        }
    }

    @Data
    public static class MCPServerConfig {
        private String name;
        private String url;
        private String transport; // stdio, sse, http
        private Map<String, String> environment = new HashMap<>();
        private String command; // for stdio transport
        private List<String> args = new ArrayList<>();
    }

    @Data
    public static class SystemSettings {
        private boolean enableFileSystem = true;
        private boolean enableWebAccess = true;
        private boolean enableBashExecution = true;
        private String workingDirectory = System.getProperty("user.dir");
        private int maxTokens = 4096;
        private double temperature = 0.7;
    }

    /**
     * Get the fallback provider (from GENERAL_KNOWLEDGE task model)
     * @return LLMProvider for GENERAL_KNOWLEDGE, or null if not configured
     */
    public LLMProvider getFallbackProvider() {
        TaskModelConfig fallbackConfig = taskModels.get(TaskType.GENERAL_KNOWLEDGE);
        return fallbackConfig != null ? fallbackConfig.getProvider() : null;
    }

    /**
     * Get the fallback model name (from GENERAL_KNOWLEDGE task model)
     * @return Model name for GENERAL_KNOWLEDGE, or null if not configured
     */
    public String getFallbackModel() {
        TaskModelConfig fallbackConfig = taskModels.get(TaskType.GENERAL_KNOWLEDGE);
        return fallbackConfig != null ? fallbackConfig.getModel() : null;
    }

    public enum LLMProvider {
        OPENAI,
        ANTHROPIC,
        OLLAMA
    }

    public enum TaskType {
        GENERAL_KNOWLEDGE("General Knowledge", "General purpose tasks, Q&A, reasoning"),
        CODING("Coding", "Code generation, debugging, refactoring"),
        TEXT_TO_SPEECH("Text-to-Speech (TTS)", "Converting text to spoken audio"),
        SPEECH_TO_TEXT("Speech-to-Text (STT)", "Audio transcription and speech recognition"),
        IMAGE_ANALYSIS("Image Analysis", "Image understanding, vision, and analysis"),
        IMAGE_GENERATION("Image Generation", "Creating images from text descriptions"),
        VIDEO_ANALYSIS("Video Analysis", "Video understanding and processing"),
        VIDEO_GENERATION("Video Generation", "Creating or editing videos");

        private final String displayName;
        private final String description;

        TaskType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }
    }
}

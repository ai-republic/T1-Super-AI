package com.airepublic.t1.api.dto;

import com.airepublic.t1.model.AgentConfiguration;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigurationResponse {
    private Map<AgentConfiguration.LLMProvider, LLMConfigInfo> llmConfigs;
    private Map<AgentConfiguration.TaskType, TaskModelInfo> taskModels;
    // GENERAL_KNOWLEDGE task serves as fallback for all tasks

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LLMConfigInfo {
        private String model;
        private String baseUrl;
        private Boolean hasApiKey;
        private String apiKey; // The actual API key (for editing in config UI)
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskModelInfo {
        private AgentConfiguration.LLMProvider provider;
        private String model;
        private String displayName;
        private Boolean hasCustomApiKey; // Indicates if task has its own API key
        private String apiKey; // The actual API key (for editing in config UI)
        private String baseUrl; // Optional: custom base URL for this task
    }
}

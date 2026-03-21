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
    private AgentConfiguration.LLMProvider defaultProvider;
    private String currentModel;
    private Map<AgentConfiguration.LLMProvider, LLMConfigInfo> llmConfigs;
    private Map<AgentConfiguration.TaskType, TaskModelInfo> taskModels;
    private Boolean autoModelSelectionEnabled;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LLMConfigInfo {
        private String model;
        private String baseUrl;
        private Boolean hasApiKey;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskModelInfo {
        private AgentConfiguration.LLMProvider provider;
        private String model;
        private String displayName;
    }
}

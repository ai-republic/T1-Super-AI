package com.airepublic.t1.api.dto;

import com.airepublic.t1.model.AgentConfiguration;
import lombok.Data;

import java.util.Map;

/**
 * Request DTO for batch updating multiple task-specific model configurations.
 */
@Data
public class UpdateTaskModelsRequest {
    private Map<AgentConfiguration.TaskType, TaskModelUpdate> taskModels;

    @Data
    public static class TaskModelUpdate {
        private AgentConfiguration.LLMProvider provider;
        private String model;
        private String apiKey; // Optional: if not specified, uses provider's general API key
        private String baseUrl; // Optional: if not specified, uses provider's default base URL
    }
}

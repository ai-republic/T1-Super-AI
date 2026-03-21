package com.airepublic.t1.api.dto;

import com.airepublic.t1.model.AgentConfiguration;
import lombok.Data;

/**
 * Request DTO for updating a single task-specific model configuration.
 */
@Data
public class UpdateTaskModelRequest {
    private AgentConfiguration.TaskType taskType;
    private AgentConfiguration.LLMProvider provider;
    private String model;
}

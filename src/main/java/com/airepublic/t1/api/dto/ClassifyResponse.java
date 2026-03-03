package com.airepublic.t1.api.dto;

import com.airepublic.t1.model.AgentConfiguration;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassifyResponse {
    private String prompt;
    private AgentConfiguration.TaskType taskType;
    private String taskTypeDisplayName;
    private String detailedAnalysis;
    private AgentConfiguration.LLMProvider selectedProvider;
    private String selectedModel;
    private Boolean usingDefault;
}

package com.airepublic.t1.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to switch to a different agent")
public class SwitchAgentRequest {
    @Schema(description = "Name of the agent to switch to", example = "code-helper", required = true)
    private String agentName;
}

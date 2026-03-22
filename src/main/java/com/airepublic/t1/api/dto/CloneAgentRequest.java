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
@Schema(description = "Request to clone an agent from one team to another")
public class CloneAgentRequest {
    @Schema(description = "Name of the agent to clone", example = "CodeHelper", required = true)
    private String agentName;

    @Schema(description = "Source team name", example = "Frontend", required = true)
    private String sourceTeam;

    @Schema(description = "Target team name to clone the agent to", example = "Backend", required = true)
    private String targetTeam;
}

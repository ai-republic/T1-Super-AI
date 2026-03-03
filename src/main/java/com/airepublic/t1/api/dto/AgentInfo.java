package com.airepublic.t1.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Agent information including status, metadata, role, and purpose")
public class AgentInfo {
    @Schema(description = "Unique name of the agent", example = "master")
    private String name;

    @Schema(description = "Role or title of the agent", example = "Senior Java Developer")
    private String role;

    @Schema(description = "Primary purpose or objective of the agent", example = "Assist with Java programming and Spring Boot applications")
    private String purpose;

    @Schema(description = "Current status of the agent", example = "active", allowableValues = {"active", "idle", "stopped"})
    private String status;

    @Schema(description = "Timestamp when the agent was created", example = "2026-03-03T07:30:00")
    private LocalDateTime createdAt;

    @Schema(description = "Timestamp of the agent's last activity", example = "2026-03-03T07:45:00")
    private LocalDateTime lastActiveAt;

    @Schema(description = "Number of conversation entries in the agent's history", example = "12")
    private Integer conversationCount;

    @Schema(description = "Whether this agent is the currently active agent", example = "true")
    private Boolean isCurrentAgent;
}

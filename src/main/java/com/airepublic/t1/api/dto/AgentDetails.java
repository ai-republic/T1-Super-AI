package com.airepublic.t1.api.dto;

import com.airepublic.t1.model.AgentConfiguration.LLMProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Detailed agent information including full CHARACTER.md profile
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Detailed agent information including full CHARACTER.md profile and configuration")
public class AgentDetails {
    // Basic info
    @Schema(description = "Unique name of the agent", example = "java-expert")
    private String name;

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

    // CHARACTER.md fields
    @Schema(description = "Role or title of the agent", example = "Senior Java Developer")
    private String role;

    @Schema(description = "Primary purpose or objective of the agent",
            example = "Assist developers with Java programming and Spring Boot applications")
    private String purpose;

    @Schema(description = "Personality traits and characteristics",
            example = "Professional, patient, and detail-oriented. Explains complex concepts clearly.")
    private String personality;

    @Schema(description = "Communication style preferences",
            example = "Uses clear language with code examples. Asks clarifying questions before providing solutions.")
    private String communicationStyle;

    @Schema(description = "Areas of specialization and expertise",
            example = "Java, Spring Boot, Spring Data JPA, REST APIs, microservices architecture, unit testing")
    private String specialties;

    @Schema(description = "Constraints, limitations, or guidelines",
            example = "Does not write production code without tests. Always suggests security best practices.")
    private String constraints;

    // LLM Configuration fields
    @Schema(description = "LLM provider configured for this agent",
            example = "OPENAI",
            allowableValues = {"OPENAI", "ANTHROPIC", "OLLAMA"})
    private LLMProvider provider;

    @Schema(description = "Model name configured for this agent",
            example = "gpt-4o")
    private String model;
}

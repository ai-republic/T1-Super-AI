package com.airepublic.t1.api.dto;

import com.airepublic.t1.model.AgentConfiguration.LLMProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating an existing agent's configuration
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to update agent configuration including CHARACTER.md fields. All fields are optional - only provided fields will be updated.")
public class UpdateAgentRequest {
    @Schema(description = "Role or title of the agent", example = "Senior Code Assistant")
    private String role;

    @Schema(description = "Purpose or primary objective of the agent",
            example = "Assist developers with code writing, debugging, and architecture decisions")
    private String purpose;

    @Schema(description = "Personality traits and characteristics",
            example = "Professional, patient, detail-oriented, and encouraging")
    private String personality;

    @Schema(description = "Communication style preferences",
            example = "Uses clear, concise language with code examples")
    private String style;

    @Schema(description = "Areas of specialization and expertise",
            example = "Java, Spring Boot, REST APIs, database design")
    private String specialization;

    @Schema(description = "Emoji usage preference",
            example = "MODERATE",
            allowableValues = {"NONE", "MINIMAL", "MODERATE", "ENTHUSIASTIC"})
    private String emojiPreference;

    @Schema(description = "Behavioral guidelines, instructions, and constraints",
            example = "Always explain reasoning behind suggestions. Does not write production code without tests.")
    private String guidelines;

    @Schema(description = "LLM provider to use", example = "OPENAI", allowableValues = {"OPENAI", "ANTHROPIC", "OLLAMA"})
    private LLMProvider provider;

    @Schema(description = "Model name to use with the provider", example = "gpt-4o")
    private String model;
}

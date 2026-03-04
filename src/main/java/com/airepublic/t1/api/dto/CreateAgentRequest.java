package com.airepublic.t1.api.dto;

import com.airepublic.t1.model.AgentConfiguration.LLMProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to create a new agent with full CHARACTER.md profile")
public class CreateAgentRequest {
    @Schema(description = "Unique name for the agent", example = "code-helper", required = true)
    private String name;

    // CHARACTER.md fields
    @Schema(description = "Role or title of the agent", example = "Senior Code Assistant", required = false)
    private String role;

    @Schema(description = "Purpose or primary objective of the agent",
            example = "Assist developers with code writing, debugging, and architecture decisions",
            required = false)
    private String purpose;

    @Schema(description = "Personality traits and characteristics",
            example = "Professional, patient, detail-oriented, and encouraging. Explains concepts clearly and adapts to the user's skill level.",
            required = false)
    private String personality;

    @Schema(description = "Communication style preferences",
            example = "Uses clear, concise language with code examples. Asks clarifying questions when needed. Provides step-by-step explanations.",
            required = false)
    private String communicationStyle;

    @Schema(description = "Areas of specialization and expertise",
            example = "Java, Spring Boot, REST APIs, database design, unit testing, and code optimization",
            required = false)
    private String specialization;

    @Schema(description = "Emoji usage preference",
            example = "sparingly",
            allowableValues = {"freely", "sparingly", "none"},
            required = false)
    private String emojiPreference;

    @Schema(description = "Constraints, limitations, or guidelines",
            example = "Does not write production database queries without explicit approval. Always suggests security best practices.",
            required = false)
    private String constraints;

    // Agent configuration fields
    @Schema(description = "Context or system prompt for the agent",
            example = "You are an expert Java developer with 10+ years of experience in enterprise applications.",
            required = false)
    private String context;

    @Schema(description = "LLM provider to use",
            example = "OPENAI",
            allowableValues = {"OPENAI", "ANTHROPIC", "OLLAMA"},
            required = false)
    private LLMProvider provider;

    @Schema(description = "Model name to use with the provider",
            example = "gpt-4o",
            required = false)
    private String model;
}

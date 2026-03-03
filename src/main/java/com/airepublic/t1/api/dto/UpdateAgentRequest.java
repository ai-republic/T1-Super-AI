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
@Schema(description = "Request to update agent configuration. All fields are optional - only provided fields will be updated.")
public class UpdateAgentRequest {
    @Schema(description = "Role or title of the agent", example = "Code Assistant")
    private String role;

    @Schema(description = "Context or system prompt for the agent", example = "You are a helpful coding assistant specializing in Java")
    private String context;

    @Schema(description = "LLM provider to use", example = "OPENAI", allowableValues = {"OPENAI", "ANTHROPIC", "OLLAMA"})
    private LLMProvider provider;

    @Schema(description = "Model name to use with the provider", example = "gpt-4o")
    private String model;
}

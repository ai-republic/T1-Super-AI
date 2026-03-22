package com.airepublic.t1.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Response DTO for agent message processing
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response from agent message processing including the response text and metadata")
public class AgentMessageResponse {
    @Schema(description = "The agent's response to the message", example = "Here's a Java implementation of a sorting function...")
    private String response;

    @Schema(description = "Name of the agent that processed the message", example = "code-helper")
    private String agentName;

    @Schema(description = "The LLM model used to generate the response", example = "OPENAI/gpt-4o")
    private String modelUsed;

    @Schema(description = "Timestamp when the response was generated", example = "2026-03-03T08:05:00")
    private LocalDateTime timestamp;

    @Schema(description = "Time taken to process the message in milliseconds", example = "1250")
    private Long responseTimeMs;
}

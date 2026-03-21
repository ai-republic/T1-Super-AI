package com.airepublic.t1.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for sending a message to a specific agent
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to send a message to an agent")
public class AgentMessageRequest {
    @Schema(description = "The message/prompt to send to the agent", example = "Help me write a sorting function in Java", required = true)
    private String message;

    @Schema(description = "Whether to stream the response (not yet implemented)", example = "false")
    private Boolean stream; // Whether to stream response
}

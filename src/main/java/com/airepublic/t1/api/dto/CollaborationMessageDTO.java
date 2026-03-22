package com.airepublic.t1.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Data Transfer Object for agent collaboration messages.
 * Used to communicate tool calls and inter-agent messages to UI clients.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollaborationMessageDTO {

    /**
     * Type of collaboration event.
     * Values: "tool_call", "tool_result", "agent_communication", "agent_response"
     */
    private String type;

    /**
     * Name of the agent initiating the action.
     */
    private String fromAgent;

    /**
     * Name of the target agent (for agent-to-agent communication).
     */
    private String toAgent;

    /**
     * The message or content being communicated.
     */
    private String message;

    /**
     * Response from the target agent or tool.
     */
    private String response;

    /**
     * Name of the tool being called (for tool_call type).
     */
    private String toolName;

    /**
     * Arguments passed to the tool.
     */
    private Map<String, Object> toolArguments;

    /**
     * Result returned by the tool.
     */
    private String toolResult;

    /**
     * Whether the tool execution was successful.
     */
    private Boolean success;

    /**
     * Time taken to process the request in milliseconds.
     */
    private Long responseTimeMs;

    /**
     * Timestamp when the event occurred.
     */
    private LocalDateTime timestamp;
}

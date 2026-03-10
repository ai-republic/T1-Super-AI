package com.airepublic.t1.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationMessage {
    private String role; // user, assistant, system, tool, collaboration
    private String content;
    private String agentName; // Name of the agent that sent this message (for assistant role)
    private LocalDateTime timestamp;
    @Builder.Default
    private List<ToolCall> toolCalls = new ArrayList<>();
    private String toolCallId; // for tool response messages

    // Collaboration message fields
    private String messageType; // "tool_call", "tool_result", "agent_communication", "agent_response"
    private String fromAgent; // For collaboration messages
    private String toAgent; // For collaboration messages
    private String toolName; // For tool call messages
    private Map<String, Object> toolArguments; // For tool call details
    private String toolResult; // For tool result messages
    private Boolean success; // For tool result success/failure
    private Long responseTimeMs; // For timing information
}

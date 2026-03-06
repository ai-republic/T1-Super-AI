package com.airepublic.t1.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationMessage {
    private String role; // user, assistant, system, tool
    private String content;
    private String agentName; // Name of the agent that sent this message (for assistant role)
    private LocalDateTime timestamp;
    @Builder.Default
    private List<ToolCall> toolCalls = new ArrayList<>();
    private String toolCallId; // for tool response messages
}

package com.airepublic.t1.api.dto;

import com.airepublic.t1.model.MessageAttachment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {
    private String message;
    private String agentName; // Optional - specify which agent to use
    private Boolean stream; // Whether to stream response

    // File attachments (e.g., images for vision analysis)
    @Builder.Default
    private List<MessageAttachment> attachments = new ArrayList<>();
}

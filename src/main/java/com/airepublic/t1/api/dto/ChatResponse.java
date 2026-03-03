package com.airepublic.t1.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {
    private String response;
    private String agentName;
    private String modelUsed;
    private LocalDateTime timestamp;
    private Long responseTimeMs;
}

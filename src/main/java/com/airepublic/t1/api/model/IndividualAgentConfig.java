package com.airepublic.t1.model;

import java.time.LocalDateTime;

import com.airepublic.t1.model.AgentConfiguration.LLMProvider;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configuration for an individual agent instance.
 * Each agent can have its own role, context, and LLM provider/model settings.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class IndividualAgentConfig {
    private String name;
    private String role;
    private String purpose;
    private String specialization;
    private String style;
    private String personality;
    private String emojiPreference;
    private String guidelines;
    private String status;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime lastModifiedAt = LocalDateTime.now();
    private LLMProvider provider;
    private String model;

    public void updateLastModified() {
        lastModifiedAt = LocalDateTime.now();
    }
}

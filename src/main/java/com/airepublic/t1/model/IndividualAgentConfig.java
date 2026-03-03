package com.airepublic.t1.model;

import com.airepublic.t1.model.AgentConfiguration.LLMProvider;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Configuration for an individual agent instance.
 * Each agent can have its own role, context, and LLM provider/model settings.
 */
@Data
public class IndividualAgentConfig {
    private String name;
    private String role;
    private String context;
    private LLMProvider provider;
    private String model;
    private LocalDateTime createdAt;
    private LocalDateTime lastModifiedAt;

    public IndividualAgentConfig() {
        this.createdAt = LocalDateTime.now();
        this.lastModifiedAt = LocalDateTime.now();
    }

    public IndividualAgentConfig(String name, String role, String context, LLMProvider provider, String model) {
        this.name = name;
        this.role = role;
        this.context = context;
        this.provider = provider;
        this.model = model;
        this.createdAt = LocalDateTime.now();
        this.lastModifiedAt = LocalDateTime.now();
    }

    public void updateLastModified() {
        this.lastModifiedAt = LocalDateTime.now();
    }
}

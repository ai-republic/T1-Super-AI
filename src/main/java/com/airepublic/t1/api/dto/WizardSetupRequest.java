package com.airepublic.t1.api.dto;

import java.util.Map;
import lombok.Data;

/**
 * Request DTO for completing the setup wizard.
 *
 * Contains all user and agent configuration data collected
 * through the multi-step wizard interface.
 */
@Data
public class WizardSetupRequest {
    // LLM Configuration - Multiple providers
    private Map<String, ProviderConfig> providers;
    private String defaultProvider;

    // Task-specific models
    private Map<String, TaskModelConfig> taskModels;

    // User Profile
    private String userName;
    private String userPronouns;
    private String userWorkFocus;

    // Agent Configuration
    private String agentName;
    private String agentRole;
    private String agentPurpose;
    private String agentSpecialization;
    private String agentPersonality;
    private String communicationStyle;
    private String emojiPreference;

    @Data
    public static class ProviderConfig {
        private boolean enabled;
        private String apiKey;
        private String baseUrl;
        private String model;
    }

    @Data
    public static class TaskModelConfig {
        private String provider;
        private String model;
    }
}

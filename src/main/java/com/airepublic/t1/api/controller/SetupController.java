package com.airepublic.t1.api.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.airepublic.t1.api.dto.ApiResponse;
import com.airepublic.t1.api.dto.SetupStatusResponse;
import com.airepublic.t1.api.dto.WizardSetupRequest;
import com.airepublic.t1.config.AgentConfigurationManager;
import com.airepublic.t1.config.WorkspaceInitializer;
import com.airepublic.t1.model.AgentConfiguration;
import com.airepublic.t1.model.AgentConfiguration.LLMProvider;
import com.airepublic.t1.service.HatchingService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST controller for handling first-time setup wizard.
 *
 * This controller provides endpoints for:
 * - Checking if setup is needed
 * - Completing the setup wizard process
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/setup")
@RequiredArgsConstructor
public class SetupController {

    private final WorkspaceInitializer workspaceInitializer;
    private final HatchingService hatchingService;
    private final AgentConfigurationManager configManager;

    /**
     * Check if setup is needed (first-time launch detection)
     *
     * @return Status response indicating whether setup is needed
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<SetupStatusResponse>> getSetupStatus() {
        try {
            final boolean needsSetup = workspaceInitializer.needsHatching();

            final SetupStatusResponse response = SetupStatusResponse.builder()
                    .needsSetup(needsSetup)
                    .workspaceInitialized(!needsSetup)
                    .build();

            return ResponseEntity.ok(ApiResponse.success(response));
        } catch (final Exception e) {
            log.error("Failed to check setup status", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to check setup status: " + e.getMessage()));
        }
    }

    /**
     * Complete the setup wizard process.
     *
     * Creates:
     * - USER.md profile
     * - First agent with CHARACTER.md
     * - Agent configuration files
     *
     * @param request Setup wizard data including user and agent information
     * @return Success response or error
     */
    @PostMapping("/complete")
    public ResponseEntity<ApiResponse<Void>> completeSetup(@RequestBody final WizardSetupRequest request) {
        try {
            log.info("🥚 Starting wizard setup process...");

            // Validate request
            if (request.getAgentName() == null || request.getAgentName().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Agent name is required"));
            }

            if (request.getUserName() == null || request.getUserName().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("User name is required"));
            }

            // Check if already setup
            if (!workspaceInitializer.needsHatching()) {
                log.warn("Setup already completed - USER.md exists");
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Setup has already been completed"));
            }

            // Step 1: Configure LLM providers (multiple)
            log.info("🔧 Configuring LLM providers...");

            final AgentConfiguration config = configManager.getConfiguration();
            int configuredCount = 0;

            // Configure each enabled provider
            if (request.getProviders() != null) {
                for (final Map.Entry<String, WizardSetupRequest.ProviderConfig> entry : request.getProviders().entrySet()) {
                    final String providerName = entry.getKey();
                    final WizardSetupRequest.ProviderConfig providerConfig = entry.getValue();

                    if (providerConfig.isEnabled()) {
                        try {
                            final LLMProvider provider = LLMProvider.valueOf(providerName);

                            final AgentConfiguration.LLMConfig llmConfig = new AgentConfiguration.LLMConfig();

                            // API key (not needed for Ollama)
                            if (providerConfig.getApiKey() != null && !providerConfig.getApiKey().isEmpty()) {
                                llmConfig.setApiKey(providerConfig.getApiKey());
                            }

                            // Base URL
                            if (providerConfig.getBaseUrl() != null && !providerConfig.getBaseUrl().isEmpty()) {
                                llmConfig.setBaseUrl(providerConfig.getBaseUrl());
                            }

                            // Model
                            if (providerConfig.getModel() != null && !providerConfig.getModel().isEmpty()) {
                                llmConfig.setModel(providerConfig.getModel());
                            }

                            config.getLlmConfigs().put(provider, llmConfig);
                            configuredCount++;
                            log.info("✅ Configured {}", provider);

                        } catch (final IllegalArgumentException e) {
                            log.warn("Invalid provider name: {}", providerName);
                        }
                    }
                }
            }

            // Set default provider
            if (request.getDefaultProvider() != null) {
                try {
                    final LLMProvider defaultProvider = LLMProvider.valueOf(request.getDefaultProvider());
                    config.setDefaultProvider(defaultProvider);
                    log.info("✅ Default provider set to: {}", defaultProvider);
                } catch (final IllegalArgumentException e) {
                    log.warn("Invalid default provider: {}", request.getDefaultProvider());
                }
            }

            // Configure task-specific models if provided
            if (request.getTaskModels() != null && !request.getTaskModels().isEmpty()) {
                int taskModelsConfigured = 0;
                for (final Map.Entry<String, WizardSetupRequest.TaskModelConfig> entry : request.getTaskModels().entrySet()) {
                    final String taskTypeName = entry.getKey();
                    final WizardSetupRequest.TaskModelConfig taskModelConfig = entry.getValue();

                    // Only configure if provider is specified
                    if (taskModelConfig.getProvider() != null && !taskModelConfig.getProvider().isEmpty()) {
                        try {
                            final AgentConfiguration.TaskType taskType = AgentConfiguration.TaskType.valueOf(taskTypeName);
                            final LLMProvider taskProvider = LLMProvider.valueOf(taskModelConfig.getProvider());

                            final AgentConfiguration.TaskModelConfig taskConfig = new AgentConfiguration.TaskModelConfig();
                            taskConfig.setProvider(taskProvider);
                            if (taskModelConfig.getModel() != null && !taskModelConfig.getModel().isEmpty()) {
                                taskConfig.setModel(taskModelConfig.getModel());
                            }

                            config.getTaskModels().put(taskType, taskConfig);
                            taskModelsConfigured++;
                            log.info("✅ Configured task-specific model for {}: {} / {}",
                                    taskType, taskProvider, taskModelConfig.getModel());

                        } catch (final IllegalArgumentException e) {
                            log.warn("Invalid task type or provider: {} / {}", taskTypeName, taskModelConfig.getProvider());
                        }
                    }
                }

                log.info("✅ Task-specific models configured: {}", taskModelsConfigured);
            }

            // Save configuration
            configManager.saveConfiguration();

            log.info("✅ LLM configuration saved ({} provider{} configured)",
                    configuredCount, configuredCount != 1 ? "s" : "");

            // Step 2: Build responses map for hatching service
            final Map<String, String> responses = new HashMap<>();

            // User data
            responses.put("user_name", request.getUserName());
            responses.put("user_pronouns", request.getUserPronouns() != null ? request.getUserPronouns() : "they/them");
            responses.put("user_work_focus", request.getUserWorkFocus() != null ? request.getUserWorkFocus() : "Software Development");

            // Agent data
            responses.put("agent_name", request.getAgentName());
            responses.put("agent_role", request.getAgentRole() != null ? request.getAgentRole() : "AI Assistant");
            responses.put("agent_purpose", request.getAgentPurpose() != null ? request.getAgentPurpose() : "General purpose assistance");
            responses.put("agent_specialization", request.getAgentSpecialization() != null ? request.getAgentSpecialization() : "General purpose assistance");
            responses.put("agent_personality", request.getAgentPersonality() != null ? request.getAgentPersonality() : "Professional and helpful");
            responses.put("communication_style", request.getCommunicationStyle() != null ? request.getCommunicationStyle() : "Clear and concise");
            responses.put("emoji_preference", request.getEmojiPreference() != null ? request.getEmojiPreference() : "MODERATE");

            // Step 3: Complete the hatch process
            hatchingService.completeHatchProcess(responses);

            log.info("🎉 Wizard setup completed successfully!");
            log.info("   Default Provider: {}", request.getDefaultProvider());
            log.info("   Configured Providers: {}", configuredCount);
            log.info("   User: {} ({})", request.getUserName(), request.getUserPronouns());
            log.info("   Agent: {} - {}", request.getAgentName(), request.getAgentRole());

            return ResponseEntity.ok(ApiResponse.success("Setup completed successfully", null));

        } catch (final Exception e) {
            log.error("❌ Failed to complete setup wizard", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Failed to complete setup: " + e.getMessage()));
        }
    }
}

package com.airepublic.t1.api.controller;

import com.airepublic.t1.agent.AgentOrchestrator;
import com.airepublic.t1.api.dto.*;
import com.airepublic.t1.config.AgentConfigurationManager;
import com.airepublic.t1.model.AgentConfiguration;
import com.airepublic.t1.tools.AutoModelSelectorTool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/config")
@RequiredArgsConstructor
public class ConfigurationController {
    private final AgentConfigurationManager configManager;
    private final AgentOrchestrator orchestrator;
    private final AutoModelSelectorTool autoModelSelector;

    @GetMapping
    public ResponseEntity<ApiResponse<ConfigurationResponse>> getConfiguration() {
        try {
            AgentConfiguration config = configManager.getConfiguration();

            // Convert LLM configs
            Map<AgentConfiguration.LLMProvider, ConfigurationResponse.LLMConfigInfo> llmConfigMap =
                    config.getLlmConfigs().entrySet().stream()
                            .collect(Collectors.toMap(
                                    Map.Entry::getKey,
                                    e -> ConfigurationResponse.LLMConfigInfo.builder()
                                            .model(e.getValue().getModel())
                                            .baseUrl(e.getValue().getBaseUrl())
                                            .hasApiKey(e.getValue().getApiKey() != null && !e.getValue().getApiKey().isEmpty())
                                            .apiKey(e.getValue().getApiKey())
                                            .build()
                            ));

            // Convert task models
            Map<AgentConfiguration.TaskType, ConfigurationResponse.TaskModelInfo> taskModelMap =
                    config.getTaskModels().entrySet().stream()
                            .collect(Collectors.toMap(
                                    Map.Entry::getKey,
                                    e -> ConfigurationResponse.TaskModelInfo.builder()
                                            .provider(e.getValue().getProvider())
                                            .model(e.getValue().getModel())
                                            .displayName(e.getKey().getDisplayName())
                                            .hasCustomApiKey(e.getValue().getApiKey() != null && !e.getValue().getApiKey().isEmpty())
                                            .apiKey(e.getValue().getApiKey())
                                            .baseUrl(e.getValue().getBaseUrl())
                                            .build()
                            ));

            ConfigurationResponse response = ConfigurationResponse.builder()
                    .llmConfigs(llmConfigMap)
                    .taskModels(taskModelMap)
                    .build();

            return ResponseEntity.ok(ApiResponse.success(response));

        } catch (Exception e) {
            log.error("Error retrieving configuration", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Error retrieving configuration: " + e.getMessage()));
        }
    }

    @PutMapping("/providers")
    public ResponseEntity<ApiResponse<Void>> updateProviders(@RequestBody UpdateProvidersRequest request) {
        try {
            AgentConfiguration config = configManager.getConfiguration();
            int updatedCount = 0;

            for (Map.Entry<AgentConfiguration.LLMProvider, UpdateProvidersRequest.ProviderConfigUpdate> entry :
                    request.getProviders().entrySet()) {

                AgentConfiguration.LLMProvider provider = entry.getKey();
                UpdateProvidersRequest.ProviderConfigUpdate update = entry.getValue();

                // Get or create LLM config for this provider
                AgentConfiguration.LLMConfig llmConfig = config.getLlmConfigs()
                        .computeIfAbsent(provider, k -> new AgentConfiguration.LLMConfig());

                // Update fields only if provided
                if (update.getApiKey() != null && !update.getApiKey().isEmpty()) {
                    llmConfig.setApiKey(update.getApiKey());
                }
                if (update.getBaseUrl() != null && !update.getBaseUrl().isEmpty()) {
                    llmConfig.setBaseUrl(update.getBaseUrl());
                }
                if (update.getModel() != null && !update.getModel().isEmpty()) {
                    llmConfig.setModel(update.getModel());
                }

                updatedCount++;
            }

            configManager.saveConfiguration();
            log.info("Updated configuration for {} providers", updatedCount);

            return ResponseEntity.ok(ApiResponse.success(
                    "Updated configuration for " + updatedCount + " provider(s)", null));

        } catch (Exception e) {
            log.error("Error updating providers", e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Error updating providers: " + e.getMessage()));
        }
    }

    @GetMapping("/models")
    public ResponseEntity<ApiResponse<Map<String, Object>>> listModels() {
        try {
            AgentConfiguration config = configManager.getConfiguration();
            Map<String, Object> modelsInfo = new LinkedHashMap<>();

            // Fallback model (GENERAL_KNOWLEDGE)
            AgentConfiguration.TaskModelConfig fallbackConfig = config.getTaskModels().get(AgentConfiguration.TaskType.GENERAL_KNOWLEDGE);
            if (fallbackConfig != null) {
                Map<String, String> fallbackInfo = new LinkedHashMap<>();
                fallbackInfo.put("provider", fallbackConfig.getProvider().name());
                fallbackInfo.put("model", fallbackConfig.getModel());
                modelsInfo.put("fallbackModel", fallbackInfo);
            }

            // Provider models
            Map<String, String> providerModels = new LinkedHashMap<>();
            config.getLlmConfigs().forEach((provider, cfg) ->
                    providerModels.put(provider.name(), cfg.getModel()));
            modelsInfo.put("providerModels", providerModels);

            // Task-specific models
            Map<String, Map<String, String>> taskModels = new LinkedHashMap<>();
            config.getTaskModels().forEach((taskType, taskConfig) -> {
                Map<String, String> taskInfo = new LinkedHashMap<>();
                taskInfo.put("provider", taskConfig.getProvider().name());
                taskInfo.put("model", taskConfig.getModel());
                taskModels.put(taskType.getDisplayName(), taskInfo);
            });
            modelsInfo.put("taskModels", taskModels);

            return ResponseEntity.ok(ApiResponse.success(modelsInfo));

        } catch (Exception e) {
            log.error("Error listing models", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Error listing models: " + e.getMessage()));
        }
    }

    @PostMapping("/classify")
    public ResponseEntity<ApiResponse<ClassifyResponse>> classifyPrompt(@RequestBody ClassifyRequest request) {
        try {
            String prompt = request.getPrompt();

            // Get detailed analysis
            String detailedAnalysis = autoModelSelector.getDetailedAnalysis(prompt);

            // Get task type
            Optional<AgentConfiguration.TaskType> taskType =
                    autoModelSelector.selectTaskTypeForPrompt(prompt);

            // Determine selected model
            AgentConfiguration config = configManager.getConfiguration();
            AgentConfiguration.LLMProvider selectedProvider;
            String selectedModel;
            boolean usingFallback;

            if (taskType.isPresent()) {
                AgentConfiguration.TaskModelConfig taskConfig =
                        config.getTaskModels().get(taskType.get());
                if (taskConfig != null) {
                    selectedProvider = taskConfig.getProvider();
                    selectedModel = taskConfig.getModel();
                    usingFallback = false;
                } else {
                    // Use GENERAL_KNOWLEDGE as fallback
                    AgentConfiguration.TaskModelConfig fallbackConfig =
                            config.getTaskModels().get(AgentConfiguration.TaskType.GENERAL_KNOWLEDGE);
                    selectedProvider = fallbackConfig.getProvider();
                    selectedModel = fallbackConfig.getModel();
                    usingFallback = true;
                }
            } else {
                // Use GENERAL_KNOWLEDGE as fallback
                AgentConfiguration.TaskModelConfig fallbackConfig =
                        config.getTaskModels().get(AgentConfiguration.TaskType.GENERAL_KNOWLEDGE);
                selectedProvider = fallbackConfig.getProvider();
                selectedModel = fallbackConfig.getModel();
                usingFallback = true;
            }

            ClassifyResponse response = ClassifyResponse.builder()
                    .prompt(prompt)
                    .taskType(taskType.orElse(null))
                    .taskTypeDisplayName(taskType.map(AgentConfiguration.TaskType::getDisplayName).orElse("General Knowledge (Fallback)"))
                    .detailedAnalysis(detailedAnalysis)
                    .selectedProvider(selectedProvider)
                    .selectedModel(selectedModel)
                    .usingDefault(usingFallback)
                    .build();

            return ResponseEntity.ok(ApiResponse.success(response));

        } catch (Exception e) {
            log.error("Error classifying prompt", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Error classifying prompt: " + e.getMessage()));
        }
    }

    /**
     * Update or configure a task-specific model
     */
    @PutMapping("/task-model")
    public ResponseEntity<ApiResponse<Void>> updateTaskModel(@RequestBody UpdateTaskModelRequest request) {
        try {
            AgentConfiguration config = configManager.getConfiguration();

            AgentConfiguration.TaskType taskType = request.getTaskType();
            AgentConfiguration.LLMProvider provider = request.getProvider();
            String model = request.getModel();

            // Create or update task model config
            AgentConfiguration.TaskModelConfig taskConfig = new AgentConfiguration.TaskModelConfig(
                provider, model, request.getApiKey(), request.getBaseUrl());
            config.getTaskModels().put(taskType, taskConfig);

            configManager.saveConfiguration();

            String apiKeyInfo = (request.getApiKey() != null && !request.getApiKey().isEmpty())
                ? " (with custom API key)" : "";
            log.info("Task-specific model configured: {} -> {} / {}{}", taskType, provider, model, apiKeyInfo);

            return ResponseEntity.ok(ApiResponse.success(
                    "Task model updated: " + taskType.getDisplayName() + " -> " + provider + " / " + model + apiKeyInfo, null));

        } catch (Exception e) {
            log.error("Error updating task model", e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Error updating task model: " + e.getMessage()));
        }
    }

    /**
     * Remove a task-specific model configuration (will use default provider)
     */
    @DeleteMapping("/task-model/{taskType}")
    public ResponseEntity<ApiResponse<Void>> deleteTaskModel(@PathVariable AgentConfiguration.TaskType taskType) {
        try {
            AgentConfiguration config = configManager.getConfiguration();

            if (config.getTaskModels().remove(taskType) != null) {
                configManager.saveConfiguration();
                log.info("Task-specific model removed: {}", taskType);
                return ResponseEntity.ok(ApiResponse.success(
                        "Task model removed: " + taskType.getDisplayName() + " (will use default provider)", null));
            } else {
                return ResponseEntity.ok(ApiResponse.success(
                        "No task model configured for: " + taskType.getDisplayName(), null));
            }

        } catch (Exception e) {
            log.error("Error deleting task model", e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Error deleting task model: " + e.getMessage()));
        }
    }

    /**
     * Batch update multiple task-specific models
     */
    @PutMapping("/task-models")
    public ResponseEntity<ApiResponse<Void>> updateTaskModels(@RequestBody UpdateTaskModelsRequest request) {
        try {
            AgentConfiguration config = configManager.getConfiguration();
            int updatedCount = 0;
            int skippedCount = 0;

            for (Map.Entry<AgentConfiguration.TaskType, UpdateTaskModelsRequest.TaskModelUpdate> entry :
                    request.getTaskModels().entrySet()) {

                AgentConfiguration.TaskType taskType = entry.getKey();
                UpdateTaskModelsRequest.TaskModelUpdate update = entry.getValue();

                // Skip if provider not specified
                if (update.getProvider() == null) {
                    skippedCount++;
                    continue;
                }

                AgentConfiguration.TaskModelConfig taskConfig =
                        new AgentConfiguration.TaskModelConfig(update.getProvider(), update.getModel(),
                                update.getApiKey(), update.getBaseUrl());
                config.getTaskModels().put(taskType, taskConfig);
                updatedCount++;
            }

            configManager.saveConfiguration();

            log.info("Task models batch update: {} updated, {} skipped", updatedCount, skippedCount);

            return ResponseEntity.ok(ApiResponse.success(
                    "Task models updated: " + updatedCount + " configured, " + skippedCount + " skipped", null));

        } catch (Exception e) {
            log.error("Error updating task models", e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Error updating task models: " + e.getMessage()));
        }
    }
}

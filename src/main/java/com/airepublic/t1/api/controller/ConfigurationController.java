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
                                            .build()
                            ));

            ConfigurationResponse response = ConfigurationResponse.builder()
                    .defaultProvider(config.getDefaultProvider())
                    .currentModel(configManager.getCurrentModel())
                    .llmConfigs(llmConfigMap)
                    .taskModels(taskModelMap)
                    .autoModelSelectionEnabled(orchestrator.isAutoModelSelectionEnabled())
                    .build();

            return ResponseEntity.ok(ApiResponse.success(response));

        } catch (Exception e) {
            log.error("Error retrieving configuration", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Error retrieving configuration: " + e.getMessage()));
        }
    }

    @PutMapping("/provider")
    public ResponseEntity<ApiResponse<Void>> updateProvider(@RequestBody UpdateProviderRequest request) {
        try {
            configManager.updateProvider(request.getProvider());
            return ResponseEntity.ok(ApiResponse.success(
                    "Provider updated to: " + request.getProvider(), null));
        } catch (Exception e) {
            log.error("Error updating provider", e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Error updating provider: " + e.getMessage()));
        }
    }

    @PutMapping("/model")
    public ResponseEntity<ApiResponse<Void>> updateModel(@RequestBody UpdateModelRequest request) {
        try {
            configManager.updateModel(request.getModel());
            return ResponseEntity.ok(ApiResponse.success(
                    "Model updated to: " + request.getModel(), null));
        } catch (Exception e) {
            log.error("Error updating model", e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Error updating model: " + e.getMessage()));
        }
    }

    @GetMapping("/models")
    public ResponseEntity<ApiResponse<Map<String, Object>>> listModels() {
        try {
            AgentConfiguration config = configManager.getConfiguration();
            Map<String, Object> modelsInfo = new LinkedHashMap<>();

            // Current model
            modelsInfo.put("current", configManager.getCurrentModel());
            modelsInfo.put("currentProvider", config.getDefaultProvider());

            // Default provider models
            Map<String, String> defaultModels = new LinkedHashMap<>();
            config.getLlmConfigs().forEach((provider, cfg) ->
                    defaultModels.put(provider.name(), cfg.getModel()));
            modelsInfo.put("defaultModels", defaultModels);

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

    @PutMapping("/auto-model")
    public ResponseEntity<ApiResponse<Void>> setAutoModelSelection(@RequestBody AutoModelRequest request) {
        try {
            orchestrator.setAutoModelSelectionEnabled(request.getEnabled());
            String status = request.getEnabled() ? "enabled" : "disabled";
            return ResponseEntity.ok(ApiResponse.success(
                    "Automatic model selection " + status, null));
        } catch (Exception e) {
            log.error("Error updating auto-model selection", e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Error updating auto-model selection: " + e.getMessage()));
        }
    }

    @GetMapping("/auto-model")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAutoModelStatus() {
        try {
            Map<String, Object> status = new LinkedHashMap<>();
            status.put("enabled", orchestrator.isAutoModelSelectionEnabled());

            // Include configured task models
            AgentConfiguration config = configManager.getConfiguration();
            Map<String, Map<String, String>> taskModels = new LinkedHashMap<>();
            config.getTaskModels().forEach((taskType, taskConfig) -> {
                Map<String, String> taskInfo = new LinkedHashMap<>();
                taskInfo.put("provider", taskConfig.getProvider().name());
                taskInfo.put("model", taskConfig.getModel());
                taskModels.put(taskType.getDisplayName(), taskInfo);
            });
            status.put("taskModels", taskModels);

            return ResponseEntity.ok(ApiResponse.success(status));

        } catch (Exception e) {
            log.error("Error retrieving auto-model status", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Error retrieving status: " + e.getMessage()));
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
            boolean usingDefault;

            if (taskType.isPresent()) {
                AgentConfiguration.TaskModelConfig taskConfig =
                        config.getTaskModels().get(taskType.get());
                if (taskConfig != null) {
                    selectedProvider = taskConfig.getProvider();
                    selectedModel = taskConfig.getModel();
                    usingDefault = false;
                } else {
                    selectedProvider = config.getDefaultProvider();
                    selectedModel = config.getLlmConfigs().get(selectedProvider).getModel();
                    usingDefault = true;
                }
            } else {
                selectedProvider = config.getDefaultProvider();
                selectedModel = config.getLlmConfigs().get(selectedProvider).getModel();
                usingDefault = true;
            }

            ClassifyResponse response = ClassifyResponse.builder()
                    .prompt(prompt)
                    .taskType(taskType.orElse(null))
                    .taskTypeDisplayName(taskType.map(AgentConfiguration.TaskType::getDisplayName).orElse("General"))
                    .detailedAnalysis(detailedAnalysis)
                    .selectedProvider(selectedProvider)
                    .selectedModel(selectedModel)
                    .usingDefault(usingDefault)
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

            // Validate provider is configured
            if (!config.getLlmConfigs().containsKey(provider)) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Provider " + provider + " is not configured. Configure it first."));
            }

            // Create or update task model config
            AgentConfiguration.TaskModelConfig taskConfig = new AgentConfiguration.TaskModelConfig(provider, model);
            config.getTaskModels().put(taskType, taskConfig);

            configManager.saveConfiguration();

            log.info("Task-specific model configured: {} -> {} / {}", taskType, provider, model);

            return ResponseEntity.ok(ApiResponse.success(
                    "Task model updated: " + taskType.getDisplayName() + " -> " + provider + " / " + model, null));

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

                // Skip if provider not specified or not configured
                if (update.getProvider() == null || !config.getLlmConfigs().containsKey(update.getProvider())) {
                    skippedCount++;
                    continue;
                }

                AgentConfiguration.TaskModelConfig taskConfig =
                        new AgentConfiguration.TaskModelConfig(update.getProvider(), update.getModel());
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

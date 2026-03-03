package com.airepublic.t1.tools;

import com.airepublic.t1.model.AgentConfiguration;
import com.airepublic.t1.model.AgentConfiguration.TaskType;
import com.airepublic.t1.config.AgentConfigurationManager;
import com.airepublic.t1.service.LLMTaskClassifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Tool that automatically selects the appropriate model based on LLM-powered prompt analysis.
 * This tool uses the general model to intelligently classify the user's prompt and determines
 * which task-specific model should be used to handle the request.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AutoModelSelectorTool implements AgentTool {

    private final LLMTaskClassifier llmClassifier;
    private final AgentConfigurationManager configManager;

    @Override
    public String getName() {
        return "auto_model_selector";
    }

    @Override
    public String getDescription() {
        return "Automatically analyzes a prompt using LLM intelligence to determine the best task-specific model. " +
               "Uses the general model to classify the task type, then routes to the appropriate specialized model. " +
               "Returns the task type that should be used to process the prompt. " +
               "If no specific task type is detected, returns 'default' to use the default provider. " +
               "Parameters: prompt (string) - The user's prompt to analyze.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> promptParam = new HashMap<>();
        promptParam.put("type", "string");
        promptParam.put("description", "The user's prompt to analyze for task type detection");
        properties.put("prompt", promptParam);

        schema.put("properties", properties);
        schema.put("required", new String[]{"prompt"});

        return schema;
    }

    @Override
    public String execute(Map<String, Object> arguments) throws Exception {
        String prompt = (String) arguments.get("prompt");

        if (prompt == null || prompt.trim().isEmpty()) {
            log.warn("Empty prompt provided to auto model selector");
            return formatResult(null, "default", "Empty prompt provided");
        }

        log.info("Analyzing prompt for automatic model selection using LLM classifier");

        // Classify task type using LLM
        Optional<TaskType> detectedTaskType = llmClassifier.classifyPrompt(prompt);

        if (detectedTaskType.isEmpty()) {
            log.info("LLM classified as general task, using default provider");
            return formatResult(null, "default", "General task - using default provider");
        }

        TaskType taskType = detectedTaskType.get();
        log.info("LLM detected task type: {}", taskType);

        // Check if task-specific model is configured
        AgentConfiguration config = configManager.getConfiguration();
        AgentConfiguration.TaskModelConfig taskConfig = config.getTaskModels().get(taskType);

        if (taskConfig == null) {
            log.info("Task type {} detected but no task-specific model configured, using default", taskType);
            return formatResult(taskType, "default",
                "Task type detected but not configured: " + taskType.getDisplayName());
        }

        // Return the task type to use
        log.info("Using task-specific model: {} - {}", taskConfig.getProvider(), taskConfig.getModel());
        return formatResult(taskType, taskConfig.getProvider() + "/" + taskConfig.getModel(),
            "LLM detected task: " + taskType.getDisplayName());
    }

    /**
     * Formats the result as a structured string that can be easily parsed.
     */
    private String formatResult(TaskType taskType, String modelInfo, String reason) {
        StringBuilder result = new StringBuilder();
        result.append("Task Type: ").append(taskType != null ? taskType.name() : "DEFAULT").append("\n");
        result.append("Model: ").append(modelInfo).append("\n");
        result.append("Reason: ").append(reason).append("\n");

        if (taskType != null) {
            result.append("Display Name: ").append(taskType.getDisplayName()).append("\n");
            result.append("Description: ").append(taskType.getDescription());
        }

        return result.toString();
    }

    /**
     * Public method for programmatic access without going through the tool interface.
     * This allows the orchestrator to directly get the task type for a prompt.
     * Uses LLM classification for intelligent task detection.
     */
    public Optional<TaskType> selectTaskTypeForPrompt(String prompt) {
        return llmClassifier.classifyPrompt(prompt);
    }

    /**
     * Gets detailed classification with explanation for debugging.
     * This provides insight into why the LLM chose a particular task type.
     */
    public String getDetailedAnalysis(String prompt) {
        return llmClassifier.classifyWithExplanation(prompt);
    }
}

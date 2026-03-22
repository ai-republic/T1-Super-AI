package com.airepublic.t1.tools;

import com.airepublic.t1.config.AgentConfigurationManager;
import com.airepublic.t1.model.AgentConfiguration;
import com.airepublic.t1.model.AgentConfiguration.TaskModelConfig;
import com.airepublic.t1.model.AgentConfiguration.TaskType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Tool that suggests switching to a task-specific model for better results.
 *
 * This tool doesn't actually switch models (that's handled by the orchestrator),
 * but it provides guidance to the agent about when a task-specific model
 * would be more appropriate for the current task.
 *
 * Example use cases:
 * - Agent detects user wants to analyze an image: suggest IMAGE_ANALYSIS model
 * - Agent sees a coding request: recommend CODING model
 * - Agent identifies speech transcription need: advise SPEECH_TO_TEXT model
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SwitchToTaskModelTool implements AgentTool {

    private final AgentConfigurationManager configManager;

    @Override
    public String getName() {
        return "suggest_task_model";
    }

    @Override
    public String getDescription() {
        return "Suggests switching to a task-specific model when appropriate. " +
               "Use this to inform the user that a specialized model would provide better results " +
               "for their specific task. Returns information about whether the task model is configured " +
               "and how to use it. " +
               "Parameters: task_type (string) - The task type that would be more suitable, " +
               "reason (string) - Why this model would be better for the current task.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new HashMap<>();

        // task_type parameter
        Map<String, Object> taskTypeParam = new HashMap<>();
        taskTypeParam.put("type", "string");
        taskTypeParam.put("description", "The task type that would be more suitable");
        taskTypeParam.put("enum", new String[]{
            "GENERAL_KNOWLEDGE", "CODING", "SPEECH_TO_TEXT", "TEXT_TO_SPEECH",
            "IMAGE_ANALYSIS", "IMAGE_GENERATION", "VIDEO_ANALYSIS", "VIDEO_GENERATION"
        });
        properties.put("task_type", taskTypeParam);

        // reason parameter
        Map<String, Object> reasonParam = new HashMap<>();
        reasonParam.put("type", "string");
        reasonParam.put("description", "Explanation of why this task model would be better");
        properties.put("reason", reasonParam);

        schema.put("properties", properties);
        schema.put("required", new String[]{"task_type", "reason"});

        return schema;
    }

    @Override
    public String execute(Map<String, Object> arguments) throws Exception {
        String taskTypeStr = (String) arguments.get("task_type");
        String reason = (String) arguments.get("reason");

        if (taskTypeStr == null || taskTypeStr.trim().isEmpty()) {
            log.error("Task type not provided");
            return "Error: task_type parameter is required";
        }

        try {
            TaskType taskType = TaskType.valueOf(taskTypeStr.toUpperCase());
            AgentConfiguration config = configManager.getConfiguration();
            TaskModelConfig taskConfig = config.getTaskModels().get(taskType);

            StringBuilder suggestion = new StringBuilder();
            suggestion.append("💡 Recommendation: ").append(taskType.getDisplayName()).append(" Model\n\n");
            suggestion.append("Reason: ").append(reason != null ? reason : "This task would benefit from a specialized model").append("\n\n");

            if (taskConfig != null) {
                suggestion.append("✅ Good news! You have a ").append(taskType.getDisplayName())
                          .append(" model configured:\n");
                suggestion.append("   Provider: ").append(taskConfig.getProvider()).append("\n");
                suggestion.append("   Model: ").append(taskConfig.getModel()).append("\n\n");
                suggestion.append("To use it, the agent can call: execute_with_task_model(task_type=\"")
                          .append(taskType.name()).append("\", prompt=\"your prompt\")\n");
                suggestion.append("Or enable auto-model selection to do this automatically.");
            } else {
                suggestion.append("⚠️ No ").append(taskType.getDisplayName())
                          .append(" model is currently configured.\n\n");
                suggestion.append("You can configure one using:\n");
                suggestion.append("- WebUI: Configuration → Enable auto-model selection → Configure ")
                          .append(taskType.getDisplayName()).append("\n");
                suggestion.append("- CLI: /task-model set ").append(taskType.name())
                          .append(" <provider> <model>\n\n");
                suggestion.append("Without a specific model, the default provider (")
                          .append(config.getFallbackProvider()).append(") will be used.");
            }

            log.info("Suggested task model: {} for reason: {}", taskType, reason);
            return suggestion.toString();

        } catch (IllegalArgumentException e) {
            log.error("Invalid task type: {}", taskTypeStr);
            return "Error: Invalid task_type. " +
                   "Available: GENERAL_KNOWLEDGE, CODING, SPEECH_TO_TEXT, TEXT_TO_SPEECH, " +
                   "IMAGE_ANALYSIS, IMAGE_GENERATION, VIDEO_ANALYSIS, VIDEO_GENERATION";
        }
    }
}

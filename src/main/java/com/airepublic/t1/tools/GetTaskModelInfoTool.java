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
 * Tool that provides information about configured task-specific models.
 *
 * This tool allows agents to:
 * - Query which models are configured for specific task types
 * - Check if a particular task has a specialized model
 * - List all available task-specific model configurations
 * - Understand what capabilities are available
 *
 * Example use cases:
 * - Agent wants to know if image analysis is available
 * - Agent needs to check which model is used for coding tasks
 * - Agent wants to list all configured task-specific models
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GetTaskModelInfoTool implements AgentTool {

    private final AgentConfigurationManager configManager;

    @Override
    public String getName() {
        return "get_task_model_info";
    }

    @Override
    public String getDescription() {
        return "Gets information about configured task-specific models. " +
               "Returns details about which AI models are configured for specific task types. " +
               "Can query a specific task type or list all configured models. " +
               "Parameters: task_type (string, optional) - Specific task type to query. " +
               "If omitted, returns all configured task models. " +
               "Available task types: GENERAL_KNOWLEDGE, CODING, SPEECH_TO_TEXT, TEXT_TO_SPEECH, " +
               "IMAGE_ANALYSIS, IMAGE_GENERATION, VIDEO_ANALYSIS, VIDEO_GENERATION.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new HashMap<>();

        // task_type parameter (optional)
        Map<String, Object> taskTypeParam = new HashMap<>();
        taskTypeParam.put("type", "string");
        taskTypeParam.put("description", "The task type to query (optional). If omitted, returns all configured models.");
        taskTypeParam.put("enum", new String[]{
            "GENERAL_KNOWLEDGE", "CODING", "SPEECH_TO_TEXT", "TEXT_TO_SPEECH",
            "IMAGE_ANALYSIS", "IMAGE_GENERATION", "VIDEO_ANALYSIS", "VIDEO_GENERATION"
        });
        properties.put("task_type", taskTypeParam);

        schema.put("properties", properties);
        // No required fields - all optional

        return schema;
    }

    @Override
    public String execute(Map<String, Object> arguments) throws Exception {
        String taskTypeStr = (String) arguments.get("task_type");
        AgentConfiguration config = configManager.getConfiguration();

        // If specific task type requested
        if (taskTypeStr != null && !taskTypeStr.trim().isEmpty()) {
            try {
                TaskType taskType = TaskType.valueOf(taskTypeStr.toUpperCase());
                return getTaskModelInfo(taskType, config);
            } catch (IllegalArgumentException e) {
                log.error("Invalid task type: {}", taskTypeStr);
                return "Error: Invalid task_type. " +
                       "Available: GENERAL_KNOWLEDGE, CODING, SPEECH_TO_TEXT, TEXT_TO_SPEECH, " +
                       "IMAGE_ANALYSIS, IMAGE_GENERATION, VIDEO_ANALYSIS, VIDEO_GENERATION";
            }
        }

        // Return all configured task models
        return getAllTaskModelsInfo(config);
    }

    /**
     * Gets information about a specific task model
     */
    private String getTaskModelInfo(TaskType taskType, AgentConfiguration config) {
        TaskModelConfig taskConfig = config.getTaskModels().get(taskType);

        if (taskConfig == null) {
            return String.format(
                "Task Type: %s\n" +
                "Status: Not configured\n" +
                "Default: Will use default provider (%s)\n" +
                "Description: %s",
                taskType.getDisplayName(),
                config.getFallbackProvider(),
                taskType.getDescription()
            );
        }

        return String.format(
            "Task Type: %s\n" +
            "Status: Configured ✅\n" +
            "Provider: %s\n" +
            "Model: %s\n" +
            "Description: %s",
            taskType.getDisplayName(),
            taskConfig.getProvider(),
            taskConfig.getModel(),
            taskType.getDescription()
        );
    }

    /**
     * Gets information about all configured task models
     */
    private String getAllTaskModelsInfo(AgentConfiguration config) {
        StringBuilder info = new StringBuilder();
        info.append("📊 Task-Specific Model Configuration\n");
        info.append("─".repeat(60)).append("\n\n");

        info.append("Default Provider: ").append(config.getFallbackProvider()).append("\n");
        info.append("Auto-Model Selection: ")
            .append(config.getTaskModels().isEmpty() ? "❌ No task models configured" : "✅ Enabled")
            .append("\n\n");

        if (config.getTaskModels().isEmpty()) {
            info.append("No task-specific models configured.\n");
            info.append("All tasks will use the default provider.\n\n");
            info.append("To configure task-specific models:\n");
            info.append("- WebUI: Enable auto-model selection in configuration\n");
            info.append("- CLI: Run /task-model config\n");
            return info.toString();
        }

        info.append("Configured Task Models:\n\n");

        // Group by category
        info.append("📝 Text & Reasoning:\n");
        appendTaskInfo(info, TaskType.GENERAL_KNOWLEDGE, config);
        appendTaskInfo(info, TaskType.CODING, config);

        info.append("\n🎤 Audio:\n");
        appendTaskInfo(info, TaskType.SPEECH_TO_TEXT, config);
        appendTaskInfo(info, TaskType.TEXT_TO_SPEECH, config);

        info.append("\n🖼️ Image:\n");
        appendTaskInfo(info, TaskType.IMAGE_ANALYSIS, config);
        appendTaskInfo(info, TaskType.IMAGE_GENERATION, config);

        info.append("\n🎬 Video:\n");
        appendTaskInfo(info, TaskType.VIDEO_ANALYSIS, config);
        appendTaskInfo(info, TaskType.VIDEO_GENERATION, config);

        info.append("\n").append("─".repeat(60)).append("\n");
        info.append("💡 Use execute_with_task_model to run prompts with specific models");

        return info.toString();
    }

    /**
     * Appends task model info to the string builder
     */
    private void appendTaskInfo(StringBuilder info, TaskType taskType, AgentConfiguration config) {
        TaskModelConfig taskConfig = config.getTaskModels().get(taskType);

        if (taskConfig != null) {
            info.append(String.format("  ✅ %-25s %s - %s\n",
                taskType.getDisplayName() + ":",
                taskConfig.getProvider(),
                taskConfig.getModel()
            ));
        } else {
            info.append(String.format("  ⏭️  %-25s (using default: %s)\n",
                taskType.getDisplayName() + ":",
                config.getFallbackProvider()
            ));
        }
    }
}

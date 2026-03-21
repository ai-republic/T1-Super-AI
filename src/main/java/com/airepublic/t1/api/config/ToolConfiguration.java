package com.airepublic.t1.config;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/**
 * Configuration model for dynamically loaded tools.
 * Tools can be configured via JSON files in .t1-super-ai/tools/
 */
@Data
public class ToolConfiguration {
    @JsonProperty("name")
    private String name;

    @JsonProperty("description")
    private String description;

    @JsonProperty("enabled")
    private boolean enabled = true;

    @JsonProperty("type")
    private ToolType type;

    @JsonProperty("className")
    private String className;

    @JsonProperty("mcpServerName")
    private String mcpServerName;

    @JsonProperty("mcpToolName")
    private String mcpToolName;

    @JsonProperty("parameters")
    private Map<String, Object> parameters;

    @JsonProperty("inputSchema")
    private Map<String, Object> inputSchema;

    @JsonProperty("executionScript")
    private String executionScript;

    @JsonProperty("scriptLanguage")
    private String scriptLanguage = "bash";

    public enum ToolType {
        JAVA, // Load from Java class (via reflection or plugin)
        MCP_TOOL,        // Delegate to MCP server tool
        SCRIPT,          // Execute script (bash, python, etc.)
        HTTP_API,        // Call HTTP API
        CUSTOM           // Custom implementation
    }
}

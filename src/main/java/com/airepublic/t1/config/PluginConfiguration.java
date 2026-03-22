package com.airepublic.t1.config;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

/**
 * Configuration model for dynamically loaded plugins.
 * Plugins can be configured via JSON files in .t1-super-ai/plugins/
 */
@Data
public class PluginConfiguration {
    @JsonProperty("name")
    private String name;

    @JsonProperty("version")
    private String version;

    @JsonProperty("description")
    private String description;

    @JsonProperty("enabled")
    private boolean enabled = true;

    @JsonProperty("type")
    private PluginType type;

    @JsonProperty("jarPath")
    private String jarPath;

    @JsonProperty("mainClass")
    private String mainClass;

    @JsonProperty("arguments")
    private Map<String, Object> arguments;

    @JsonProperty("tools")
    private List<String> tools;

    @JsonProperty("autoLoad")
    private boolean autoLoad = true;

    public enum PluginType {
        JAVA, // Load from JAR or class file
        SCRIPT // Load script-based plugin (e.g., Python, Node.js)
    }
}

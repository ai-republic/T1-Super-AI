package com.airepublic.t1.plugins;

import com.airepublic.t1.tools.AgentTool;
import com.airepublic.t1.tools.PluginTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * PluginManager handles plugin loading and management.
 * Supports 5 plugin types as defined in USAGE.md:
 * 1. JAVA (JAR)
 * 2. JAVA (Maven)
 * 3. SCRIPT (JavaScript/Node.js)
 * 4. SCRIPT (Python)
 * 5. HTTP_API
 *
 * Plugins are collections of tools grouped by similar context.
 */
@Slf4j
@Component
public class PluginManager {
    // Use Paths.get() for cross-platform compatibility (Windows, Linux, macOS)
    private static final String PLUGINS_DIR = Paths.get(System.getProperty("user.home"), ".t1-super-ai", "plugins").toString();

    private final Map<String, PluginInfo> loadedPlugins = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * PluginInfo represents a loaded plugin with its metadata and tools
     */
    @Data
    public static class PluginInfo {
        private String name;
        private String description;
        private String version;
        private String type; // JAVA, SCRIPT, HTTP_API
        private boolean enabled;
        private Map<String, AgentTool> tools;
    }

    public PluginManager() {
        initializeDirectories();
    }

    @PostConstruct
    public void initialize() {
        loadAllPlugins();
        log.info("PluginManager initialized with {} plugins", loadedPlugins.size());
    }

    private void initializeDirectories() {
        try {
            Files.createDirectories(Paths.get(PLUGINS_DIR));
            log.info("Plugins directory initialized: {}", PLUGINS_DIR);
        } catch (Exception e) {
            log.error("Error creating plugins directory", e);
        }
    }

    /**
     * Load all plugins from the plugins directory.
     * Each plugin is a directory containing a plugin.json file.
     */
    public void loadAllPlugins() {
        try {
            Path pluginsPath = Paths.get(PLUGINS_DIR);
            if (!Files.exists(pluginsPath)) {
                log.warn("Plugins directory does not exist: {}", PLUGINS_DIR);
                return;
            }

            try (Stream<Path> paths = Files.list(pluginsPath)) {
                paths.filter(Files::isDirectory)
                        .forEach(this::loadPluginFromDirectory);
            }
        } catch (Exception e) {
            log.error("Error loading plugins", e);
        }
    }

    /**
     * Load a plugin from a directory containing plugin.json
     */
    public void loadPluginFromDirectory(Path pluginDir) {
        String pluginName = pluginDir.getFileName().toString();
        Path pluginJsonPath = pluginDir.resolve("plugin.json");

        if (!Files.exists(pluginJsonPath)) {
            log.debug("No plugin.json found in {}, skipping", pluginDir);
            return;
        }

        try {
            JsonNode pluginConfig = objectMapper.readTree(pluginJsonPath.toFile());

            // Check if plugin is enabled
            if (pluginConfig.has("enabled") && !pluginConfig.get("enabled").asBoolean()) {
                log.info("Plugin {} is disabled, skipping", pluginName);
                return;
            }

            String pluginType = pluginConfig.has("type") ? pluginConfig.get("type").asText() : "SCRIPT";

            PluginInfo pluginInfo = new PluginInfo();
            pluginInfo.setName(pluginConfig.has("name") ? pluginConfig.get("name").asText() : pluginName);
            pluginInfo.setDescription(pluginConfig.has("description") ? pluginConfig.get("description").asText() : "");
            pluginInfo.setVersion(pluginConfig.has("version") ? pluginConfig.get("version").asText() : "1.0.0");
            pluginInfo.setType(pluginType);
            pluginInfo.setEnabled(true);

            Map<String, AgentTool> tools = new HashMap<>();

            // Load tools from plugin.json
            if (pluginConfig.has("tools")) {
                JsonNode toolsNode = pluginConfig.get("tools");
                Iterator<Map.Entry<String, JsonNode>> toolIterator = toolsNode.fields();

                while (toolIterator.hasNext()) {
                    Map.Entry<String, JsonNode> toolEntry = toolIterator.next();
                    String toolName = toolEntry.getKey();
                    JsonNode toolConfig = toolEntry.getValue();

                    try {
                        AgentTool tool = createToolFromConfig(pluginDir, pluginName, pluginType, toolName, toolConfig);
                        if (tool != null) {
                            tools.put(toolName, tool);
                            log.debug("Loaded tool: {} from plugin: {}", toolName, pluginName);
                        }
                    } catch (Exception e) {
                        log.error("Error loading tool {} from plugin {}", toolName, pluginName, e);
                    }
                }
            }

            pluginInfo.setTools(tools);

            if (!tools.isEmpty()) {
                loadedPlugins.put(pluginInfo.getName(), pluginInfo);
                log.info("Loaded plugin: {} v{} with {} tools", pluginInfo.getName(), pluginInfo.getVersion(), tools.size());
            } else {
                log.warn("Plugin {} has no valid tools", pluginName);
            }

        } catch (Exception e) {
            log.error("Error loading plugin from {}", pluginDir, e);
        }
    }

    /**
     * Create an AgentTool from plugin tool configuration
     */
    private AgentTool createToolFromConfig(Path pluginDir, String pluginName, String pluginType,
                                          String toolName, JsonNode toolConfig) {
        // Get input schema
        Map<String, Object> inputSchema = new HashMap<>();
        if (toolConfig.has("inputSchema")) {
            inputSchema = objectMapper.convertValue(toolConfig.get("inputSchema"), Map.class);
        }

        // Get description
        String description = toolConfig.has("description")
            ? toolConfig.get("description").asText()
            : "Tool from plugin: " + pluginName;

        // Validate tool configuration based on plugin type
        if ("JAVA".equals(pluginType)) {
            // JAVA plugins don't need executable per tool, but need jarPath at plugin level
            // Optional executable per tool for multi-class JARs
        } else if ("SCRIPT".equals(pluginType)) {
            // SCRIPT plugins need executable per tool
            if (!toolConfig.has("executable")) {
                log.warn("Tool {} in SCRIPT plugin {} has no executable defined", toolName, pluginName);
                return null;
            }
            String executable = toolConfig.get("executable").asText();
            Path executablePath = pluginDir.resolve(executable);
            if (!Files.exists(executablePath)) {
                log.warn("Executable not found for tool {}: {}", toolName, executablePath);
                return null;
            }
        } else if ("HTTP_API".equals(pluginType)) {
            // HTTP_API plugins need url and method
            if (!toolConfig.has("url") || !toolConfig.has("method")) {
                log.warn("Tool {} in HTTP_API plugin {} missing url or method", toolName, pluginName);
                return null;
            }
        }

        // Create unified PluginTool that handles all types
        return new PluginTool(toolName, description, pluginType, pluginDir, toolConfig, inputSchema);
    }

    /**
     * Get all tools from all loaded plugins
     */
    public List<AgentTool> getAllPluginTools() {
        List<AgentTool> tools = new ArrayList<>();
        for (PluginInfo plugin : loadedPlugins.values()) {
            if (plugin.getTools() != null) {
                tools.addAll(plugin.getTools().values());
            }
        }
        return tools;
    }

    /**
     * Get tools from a specific plugin
     */
    public Map<String, AgentTool> getPluginTools(String pluginName) {
        PluginInfo plugin = loadedPlugins.get(pluginName);
        return plugin != null ? plugin.getTools() : Collections.emptyMap();
    }

    /**
     * Get plugin info by name
     */
    public PluginInfo getPlugin(String name) {
        return loadedPlugins.get(name);
    }

    /**
     * Get all loaded plugins
     */
    public Collection<PluginInfo> getAllPlugins() {
        return loadedPlugins.values();
    }

    /**
     * Unload a plugin
     */
    public void unloadPlugin(String name) {
        PluginInfo plugin = loadedPlugins.remove(name);
        if (plugin != null) {
            log.info("Unloaded plugin: {}", name);
        }
    }

    /**
     * Reload a plugin by name
     */
    public void reloadPlugin(String name) {
        unloadPlugin(name);
        try {
            Path pluginDir = Paths.get(PLUGINS_DIR, name);
            if (Files.exists(pluginDir) && Files.isDirectory(pluginDir)) {
                loadPluginFromDirectory(pluginDir);
            }
        } catch (Exception e) {
            log.error("Error reloading plugin: {}", name, e);
        }
    }

    /**
     * Reload all plugins
     */
    public void reloadAllPlugins() {
        loadedPlugins.clear();
        loadAllPlugins();
    }

    /**
     * Shutdown plugin manager
     */
    public void shutdown() {
        loadedPlugins.clear();
        log.info("PluginManager shutdown complete");
    }
}

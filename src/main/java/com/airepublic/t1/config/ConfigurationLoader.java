package com.airepublic.t1.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Loads and manages configuration files from the ~/.t1-super-ai directory in user's home.
 * Supports hot-reloading when configuration files change.
 */
@Slf4j
@Component
public class ConfigurationLoader {
    // Default team name when none is specified
    private static final String DEFAULT_TEAM_NAME = "Default";

    // Workspace paths (will be configured from WorkspaceInitializer)
    private Path workspacePath;
    private String workspaceDir;
    private String toolsDir;
    private String pluginsDir;
    private String mcpServersDir;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, ToolConfiguration> toolConfigs = new ConcurrentHashMap<>();
    private final Map<String, PluginConfiguration> pluginConfigs = new ConcurrentHashMap<>();
    private final Map<String, McpServerConfiguration> mcpServerConfigs = new ConcurrentHashMap<>();

    private WatchService watchService;
    private Thread watchThread;
    private boolean watching = false;

    // WorkspaceInitializer handles file creation
    private WorkspaceInitializer workspaceInitializer;

    public ConfigurationLoader() {
        // Initialize with default team workspace
        initializeDefaultPaths();
    }

    private void initializeDefaultPaths() {
        // Workspace path for team-specific data
        this.workspacePath = Paths.get(System.getProperty("user.home"), ".t1-super-ai", "workspaces", DEFAULT_TEAM_NAME);
        this.workspaceDir = workspacePath.toString();

        // Tools, plugins, and MCP servers are shared across all teams (in ~/.t1-super-ai root)
        final Path rootWorkspace = Paths.get(System.getProperty("user.home"), ".t1-super-ai");
        this.toolsDir = rootWorkspace.resolve("tools").toString();
        this.pluginsDir = rootWorkspace.resolve("plugins").toString();
        this.mcpServersDir = rootWorkspace.resolve("mcp-servers").toString();
    }

    public void setWorkspaceInitializer(WorkspaceInitializer initializer) {
        this.workspaceInitializer = initializer;
        // Update paths from workspace initializer
        this.workspacePath = Paths.get(initializer.getWorkspaceDir());
        this.workspaceDir = workspacePath.toString();

        // Tools, plugins, and MCP servers are shared across all teams (in ~/.t1-super-ai root)
        final Path rootWorkspace = Paths.get(System.getProperty("user.home"), ".t1-super-ai");
        this.toolsDir = rootWorkspace.resolve("tools").toString();
        this.pluginsDir = rootWorkspace.resolve("plugins").toString();
        this.mcpServersDir = rootWorkspace.resolve("mcp-servers").toString();
        log.info("Updated ConfigurationLoader workspace paths to: {}", workspaceDir);
    }

    /**
     * Initialize directories - called by WorkspaceInitializer
     */
    public void initializeDirectories() {
        try {
            Files.createDirectories(Paths.get(toolsDir));
            Files.createDirectories(Paths.get(pluginsDir));
            Files.createDirectories(Paths.get(mcpServersDir));
            log.info("Configuration directories initialized in {}", workspaceDir);
        } catch (IOException e) {
            log.error("Error creating configuration directories", e);
        }
    }

    /**
     * Load all configurations from the workspace folder
     */
    public void loadAllConfigurations() {
        loadToolConfigurations();
        loadPluginConfigurations();
        loadMcpServerConfigurations();
        log.info("Loaded all configurations: {} tools, {} plugins, {} MCP servers",
                toolConfigs.size(), pluginConfigs.size(), mcpServerConfigs.size());
    }

    /**
     * Load tool configurations from workspace/tools/
     */
    public void loadToolConfigurations() {
        toolConfigs.clear();
        Path toolsPath = Paths.get(toolsDir);

        if (!Files.exists(toolsPath)) {
            log.warn("Tools directory does not exist: {}", toolsPath);
            return;
        }

        try (Stream<Path> paths = Files.list(toolsPath)) {
            paths.filter(p -> p.toString().endsWith(".json"))
                    .forEach(this::loadToolConfiguration);
        } catch (IOException e) {
            log.error("Error loading tool configurations", e);
        }

        log.info("Loaded {} tool configurations", toolConfigs.size());
    }

    private void loadToolConfiguration(Path configPath) {
        try {
            ToolConfiguration config = objectMapper.readValue(configPath.toFile(), ToolConfiguration.class);
            toolConfigs.put(config.getName(), config);
            log.debug("Loaded tool configuration: {}", config.getName());
        } catch (IOException e) {
            log.error("Error loading tool configuration from {}", configPath, e);
        }
    }

    /**
     * Load plugin configurations from .t1-super-ai/plugins/
     */
    public void loadPluginConfigurations() {
        pluginConfigs.clear();
        Path pluginsPath = Paths.get(pluginsDir);

        if (!Files.exists(pluginsPath)) {
            log.warn("Plugins directory does not exist: {}", pluginsPath);
            return;
        }

        try (Stream<Path> paths = Files.list(pluginsPath)) {
            paths.filter(p -> p.toString().endsWith(".json"))
                    .forEach(this::loadPluginConfiguration);
        } catch (IOException e) {
            log.error("Error loading plugin configurations", e);
        }

        log.info("Loaded {} plugin configurations", pluginConfigs.size());
    }

    private void loadPluginConfiguration(Path configPath) {
        try {
            PluginConfiguration config = objectMapper.readValue(configPath.toFile(), PluginConfiguration.class);
            pluginConfigs.put(config.getName(), config);
            log.debug("Loaded plugin configuration: {}", config.getName());
        } catch (IOException e) {
            log.error("Error loading plugin configuration from {}", configPath, e);
        }
    }

    /**
     * Load MCP server configurations from .t1-super-ai/mcp-servers/
     */
    public void loadMcpServerConfigurations() {
        mcpServerConfigs.clear();
        Path mcpServersPath = Paths.get(mcpServersDir);

        if (!Files.exists(mcpServersPath)) {
            log.warn("MCP servers directory does not exist: {}", mcpServersPath);
            return;
        }

        try (Stream<Path> paths = Files.list(mcpServersPath)) {
            paths.filter(p -> p.toString().endsWith(".json"))
                    .forEach(this::loadMcpServerConfiguration);
        } catch (IOException e) {
            log.error("Error loading MCP server configurations", e);
        }

        log.info("Loaded {} MCP server configurations", mcpServerConfigs.size());
    }

    private void loadMcpServerConfiguration(Path configPath) {
        try {
            McpServerConfiguration config = objectMapper.readValue(configPath.toFile(), McpServerConfiguration.class);
            mcpServerConfigs.put(config.getName(), config);
            log.debug("Loaded MCP server configuration: {}", config.getName());
        } catch (IOException e) {
            log.error("Error loading MCP server configuration from {}", configPath, e);
        }
    }

    /**
     * Save a tool configuration
     */
    public void saveToolConfiguration(ToolConfiguration config) throws IOException {
        Path configPath = Paths.get(toolsDir, config.getName() + ".json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(configPath.toFile(), config);
        toolConfigs.put(config.getName(), config);
        log.info("Saved tool configuration: {}", config.getName());
    }

    /**
     * Save a plugin configuration
     */
    public void savePluginConfiguration(PluginConfiguration config) throws IOException {
        Path configPath = Paths.get(pluginsDir, config.getName() + ".json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(configPath.toFile(), config);
        pluginConfigs.put(config.getName(), config);
        log.info("Saved plugin configuration: {}", config.getName());
    }

    /**
     * Save an MCP server configuration
     */
    public void saveMcpServerConfiguration(McpServerConfiguration config) throws IOException {
        Path configPath = Paths.get(mcpServersDir, config.getName() + ".json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(configPath.toFile(), config);
        mcpServerConfigs.put(config.getName(), config);
        log.info("Saved MCP server configuration: {}", config.getName());
    }

    /**
     * Start watching for configuration file changes
     */
    public void startWatching(ConfigurationChangeListener listener) {
        if (watching) {
            log.warn("Already watching for configuration changes");
            return;
        }

        try {
            watchService = FileSystems.getDefault().newWatchService();

            // Only register directories that exist
            if (Files.exists(Paths.get(toolsDir))) {
                Paths.get(toolsDir).register(watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE);
            }

            if (Files.exists(Paths.get(pluginsDir))) {
                Paths.get(pluginsDir).register(watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE);
            }

            if (Files.exists(Paths.get(mcpServersDir))) {
                Paths.get(mcpServersDir).register(watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE);
            }

            watching = true;
            watchThread = new Thread(() -> watchForChanges(listener));
            watchThread.setDaemon(true);
            watchThread.setName("ConfigurationWatcher");
            watchThread.start();

            log.info("Started watching for configuration changes");
        } catch (IOException e) {
            log.error("Error starting configuration watcher", e);
        }
    }

    private void watchForChanges(ConfigurationChangeListener listener) {
        while (watching) {
            try {
                WatchKey key = watchService.take();
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();

                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    Path filename = ev.context();

                    if (!filename.toString().endsWith(".json")) {
                        continue;
                    }

                    Path fullPath = ((Path) key.watchable()).resolve(filename);
                    handleConfigurationChange(fullPath, kind, listener);
                }

                key.reset();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error processing configuration change", e);
            }
        }
    }

    private void handleConfigurationChange(Path path, WatchEvent.Kind<?> kind, ConfigurationChangeListener listener) {
        String pathStr = path.toString();

        try {
            if (pathStr.contains(toolsDir)) {
                if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                    String fileName = path.getFileName().toString();
                    String toolName = fileName.substring(0, fileName.lastIndexOf(".json"));
                    toolConfigs.remove(toolName);
                    listener.onToolConfigurationChanged(toolName, null);
                } else {
                    loadToolConfiguration(path);
                    String fileName = path.getFileName().toString();
                    String toolName = fileName.substring(0, fileName.lastIndexOf(".json"));
                    listener.onToolConfigurationChanged(toolName, toolConfigs.get(toolName));
                }
            } else if (pathStr.contains(pluginsDir)) {
                if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                    String fileName = path.getFileName().toString();
                    String pluginName = fileName.substring(0, fileName.lastIndexOf(".json"));
                    pluginConfigs.remove(pluginName);
                    listener.onPluginConfigurationChanged(pluginName, null);
                } else {
                    loadPluginConfiguration(path);
                    String fileName = path.getFileName().toString();
                    String pluginName = fileName.substring(0, fileName.lastIndexOf(".json"));
                    listener.onPluginConfigurationChanged(pluginName, pluginConfigs.get(pluginName));
                }
            } else if (pathStr.contains(mcpServersDir)) {
                if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                    String fileName = path.getFileName().toString();
                    String serverName = fileName.substring(0, fileName.lastIndexOf(".json"));
                    mcpServerConfigs.remove(serverName);
                    listener.onMcpServerConfigurationChanged(serverName, null);
                } else {
                    loadMcpServerConfiguration(path);
                    String fileName = path.getFileName().toString();
                    String serverName = fileName.substring(0, fileName.lastIndexOf(".json"));
                    listener.onMcpServerConfigurationChanged(serverName, mcpServerConfigs.get(serverName));
                }
            }
        } catch (Exception e) {
            log.error("Error handling configuration change for {}", path, e);
        }
    }

    /**
     * Stop watching for configuration changes
     */
    public void stopWatching() {
        watching = false;
        if (watchThread != null) {
            watchThread.interrupt();
        }
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                log.error("Error closing watch service", e);
            }
        }
        log.info("Stopped watching for configuration changes");
    }

    // Getters
    public Collection<ToolConfiguration> getAllToolConfigurations() {
        return toolConfigs.values();
    }

    public Collection<PluginConfiguration> getAllPluginConfigurations() {
        return pluginConfigs.values();
    }

    public Collection<McpServerConfiguration> getAllMcpServerConfigurations() {
        return mcpServerConfigs.values();
    }

    public ToolConfiguration getToolConfiguration(String name) {
        return toolConfigs.get(name);
    }

    public PluginConfiguration getPluginConfiguration(String name) {
        return pluginConfigs.get(name);
    }

    public McpServerConfiguration getMcpServerConfiguration(String name) {
        return mcpServerConfigs.get(name);
    }

    /**
     * Interface for listening to configuration changes
     */
    public interface ConfigurationChangeListener {
        void onToolConfigurationChanged(String name, ToolConfiguration config);
        void onPluginConfigurationChanged(String name, PluginConfiguration config);
        void onMcpServerConfigurationChanged(String name, McpServerConfiguration config);
    }
}

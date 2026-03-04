package com.airepublic.t1.mcp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.airepublic.t1.plugins.PluginManager;
import com.airepublic.t1.tools.AgentTool;
import com.airepublic.t1.tools.BashTool;
import com.airepublic.t1.tools.CmdTool;
import com.airepublic.t1.tools.CreateAgentTool;
import com.airepublic.t1.tools.ListAgentsTool;
import com.airepublic.t1.tools.ListDirectoryTool;
import com.airepublic.t1.tools.ReadFileTool;
import com.airepublic.t1.tools.SendMessageToAgentTool;
import com.airepublic.t1.tools.UpdateAgentCharacterTool;
import com.airepublic.t1.tools.WebFetchTool;
import com.airepublic.t1.tools.WriteFileTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Plugin-based MCP Server that dynamically loads tools from .t1-super-ai/plugins/ folder.
 *
 * This server:
 * - Provides core tools (file operations, bash, web fetch) out of the box
 * - Scans the plugins directory for plugin.json files
 * - Loads tools defined in each plugin.json
 * - Watches the plugins directory for changes and hot-reloads
 * - Exposes all tools (core + plugin) via the Model Context Protocol
 *
 * Plugin Structure:
 * .t1-super-ai/plugins/
 *   └── plugin-name/
 *       ├── plugin.json       (tool definitions)
 *       ├── README.md         (usage documentation)
 *       └── src/              (executable scripts)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PluginBasedMCPServer {
    private static final String PLUGINS_DIR = Paths.get(System.getProperty("user.home"), ".t1-super-ai", "plugins").toString();

    // Core tools injected via constructor
    private final ReadFileTool readFileTool;
    private final WriteFileTool writeFileTool;
    private final BashTool bashTool;
    private final CmdTool cmdTool;
    private final WebFetchTool webFetchTool;
    private final ListDirectoryTool listDirectoryTool;
    private final ListAgentsTool listAgentsTool;
    private final SendMessageToAgentTool sendMessageToAgentTool;
    private final CreateAgentTool createAgentTool;
    private final UpdateAgentCharacterTool updateAgentCharacterTool;

    // Plugin manager for loading plugin-based tools
    private final PluginManager pluginManager;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private boolean initialized = false;
    private String clientName = "unknown";
    private String clientVersion = "unknown";

    // Tool storage: core tools only (plugin tools come from PluginManager)
    private final Map<String, AgentTool> coreTools = new ConcurrentHashMap<>();

    // File watcher for hot-reload
    private WatchService watchService;
    private Thread watchThread;
    private volatile boolean watching = false;

    @PostConstruct
    public void initialize() {
        // Initialize core tools first
        initializeCoreTools();

        // PluginManager loads plugins automatically via @PostConstruct
        // Just start watching for changes
        startWatching();

        log.info("PluginBasedMCPServer initialized with {} core tools, {} plugins, and {} total tools",
                coreTools.size(), pluginManager.getAllPlugins().size(), getAllTools().size());
    }

    /**
     * Initialize core tools that are always available
     */
    private void initializeCoreTools() {
        coreTools.put(readFileTool.getName(), readFileTool);
        coreTools.put(writeFileTool.getName(), writeFileTool);
        coreTools.put(bashTool.getName(), bashTool);
        coreTools.put(cmdTool.getName(), cmdTool);
        coreTools.put(webFetchTool.getName(), webFetchTool);
        coreTools.put(listDirectoryTool.getName(), listDirectoryTool);
        coreTools.put(listAgentsTool.getName(), listAgentsTool);
        coreTools.put(sendMessageToAgentTool.getName(), sendMessageToAgentTool);
        coreTools.put(createAgentTool.getName(), createAgentTool);
        coreTools.put(updateAgentCharacterTool.getName(), updateAgentCharacterTool);

        log.info("Initialized {} core tools (including inter-agent communication and agent management tools)", coreTools.size());
    }


    /**
     * Start watching the plugins directory for changes
     */
    private void startWatching() {
        try {
            watchService = FileSystems.getDefault().newWatchService();
            Path pluginsPath = Paths.get(PLUGINS_DIR);

            // Register the plugins directory
            pluginsPath.register(watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
                StandardWatchEventKinds.ENTRY_MODIFY);

            // Also register each plugin subdirectory
            Files.list(pluginsPath)
                .filter(Files::isDirectory)
                .forEach(dir -> {
                    try {
                        dir.register(watchService,
                            StandardWatchEventKinds.ENTRY_CREATE,
                            StandardWatchEventKinds.ENTRY_DELETE,
                            StandardWatchEventKinds.ENTRY_MODIFY);
                    } catch (IOException e) {
                        log.error("Error registering watch for directory: {}", dir, e);
                    }
                });

            watching = true;
            watchThread = new Thread(this::watchForChanges, "PluginWatcher");
            watchThread.setDaemon(true);
            watchThread.start();

            log.info("Started watching plugins directory for changes");

        } catch (IOException e) {
            log.error("Error starting file watcher", e);
        }
    }

    /**
     * Watch for changes in the plugins directory
     */
    private void watchForChanges() {
        while (watching) {
            try {
                WatchKey key = watchService.take();

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();

                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }

                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> pathEvent = (WatchEvent<Path>) event;
                    Path changed = pathEvent.context();

                    log.debug("Detected change in plugins directory: {} - {}", kind, changed);

                    // Reload plugins if plugin.json was modified
                    if (changed.toString().equals("plugin.json") || Files.isDirectory(changed)) {
                        // Small delay to ensure file is fully written
                        Thread.sleep(100);
                        pluginManager.reloadAllPlugins();
                    }
                }

                key.reset();

            } catch (InterruptedException e) {
                log.debug("Plugin watcher interrupted");
                break;
            } catch (Exception e) {
                log.error("Error in plugin watcher", e);
            }
        }
    }

    /**
     * Get all available tools (core + plugins)
     */
    private Collection<AgentTool> getAllTools() {
        List<AgentTool> allTools = new ArrayList<>();
        allTools.addAll(coreTools.values());
        allTools.addAll(pluginManager.getAllPluginTools());
        return allTools;
    }

    /**
     * Start the MCP server in stdio mode
     */
    public void start() {
        log.info("🚀 Starting Plugin-Based MCP Server...");
        log.info("📡 Transport: stdio");
        log.info("📁 Plugins directory: {}", PLUGINS_DIR);
        log.info("🔧 Loaded {} plugins with {} total tools", pluginManager.getAllPlugins().size(), getAllTools().size());

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                try {
                    String response = handleRequest(line);
                    if (response != null) {
                        System.out.println(response);
                        System.out.flush();
                    }
                } catch (Exception e) {
                    log.error("Error handling request", e);
                    String errorResponse = createErrorResponse(null, -32603, "Internal error: " + e.getMessage());
                    System.out.println(errorResponse);
                    System.out.flush();
                }
            }
        } catch (IOException e) {
            log.error("Error reading from stdin", e);
        }

        log.info("Plugin-Based MCP Server stopped");
    }

    /**
     * Handle MCP request externally (for background server mode)
     */
    public String handleRequestExternal(String requestJson) throws Exception {
        return handleRequest(requestJson);
    }

    /**
     * Get the number of registered tools
     */
    public int getToolCount() {
        return getAllTools().size();
    }

    private String handleRequest(String requestJson) throws Exception {
        JsonNode request = objectMapper.readTree(requestJson);

        if (!request.has("jsonrpc") || !"2.0".equals(request.get("jsonrpc").asText())) {
            return createErrorResponse(null, -32600, "Invalid JSON-RPC version");
        }

        Object id = request.has("id") ? request.get("id").asText() : null;
        String method = request.has("method") ? request.get("method").asText() : null;

        if (method == null) {
            return createErrorResponse(id, -32600, "Missing method");
        }

        log.debug("Received request: method={}, id={}", method, id);

        return switch (method) {
            case "initialize" -> handleInitialize(request, id);
            case "initialized" -> handleInitialized(request, id);
            case "tools/list" -> handleToolsList(request, id);
            case "tools/call" -> handleToolsCall(request, id);
            case "plugins/list" -> handlePluginsList(request, id);
            case "plugins/reload" -> handlePluginsReload(request, id);
            case "ping" -> handlePing(request, id);
            default -> createErrorResponse(id, -32601, "Method not found: " + method);
        };
    }

    private String handleInitialize(JsonNode request, Object id) throws Exception {
        if (!request.has("params")) {
            return createErrorResponse(id, -32602, "Missing params");
        }

        JsonNode params = request.get("params");

        if (params.has("clientInfo")) {
            JsonNode clientInfo = params.get("clientInfo");
            clientName = clientInfo.has("name") ? clientInfo.get("name").asText() : "unknown";
            clientVersion = clientInfo.has("version") ? clientInfo.get("version").asText() : "unknown";
        }

        initialized = true;
        log.info("✅ Client connected: {} v{}", clientName, clientVersion);

        Map<String, Object> result = new HashMap<>();
        result.put("protocolVersion", "2024-11-05");
        result.put("serverInfo", Map.of(
                "name", "t1-super-ai-plugin-mcp-server",
                "version", "1.0.0"
        ));
        result.put("capabilities", Map.of(
                "tools", Map.of("hotReload", true, "pluginBased", true)
        ));

        return createSuccessResponse(id, result);
    }

    private String handleInitialized(JsonNode request, Object id) throws Exception {
        log.debug("Client initialization complete");
        return null;
    }

    private String handleToolsList(JsonNode request, Object id) throws Exception {
        if (!initialized) {
            return createErrorResponse(id, -32002, "Not initialized");
        }

        List<Map<String, Object>> tools = new ArrayList<>();

        for (AgentTool tool : getAllTools()) {
            Map<String, Object> toolInfo = new HashMap<>();
            toolInfo.put("name", tool.getName());
            toolInfo.put("description", tool.getDescription());
            toolInfo.put("inputSchema", tool.getParameterSchema());
            tools.add(toolInfo);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("tools", tools);

        log.debug("Listed {} tools", tools.size());
        return createSuccessResponse(id, result);
    }

    private String handleToolsCall(JsonNode request, Object id) throws Exception {
        if (!initialized) {
            return createErrorResponse(id, -32002, "Not initialized");
        }

        if (!request.has("params")) {
            return createErrorResponse(id, -32602, "Missing params");
        }

        JsonNode params = request.get("params");

        if (!params.has("name")) {
            return createErrorResponse(id, -32602, "Missing tool name");
        }

        String toolName = params.get("name").asText();
        Map<String, Object> arguments = new HashMap<>();

        if (params.has("arguments")) {
            arguments = objectMapper.convertValue(params.get("arguments"), Map.class);
        }

        log.debug("Calling tool: {} with arguments: {}", toolName, arguments);

        // Find tool by name (check core tools first, then plugins)
        AgentTool tool = coreTools.get(toolName);

        if (tool == null) {
            // Search through all plugin tools
            for (AgentTool pluginTool : pluginManager.getAllPluginTools()) {
                if (pluginTool.getName().equals(toolName)) {
                    tool = pluginTool;
                    break;
                }
            }
        }

        if (tool == null) {
            return createErrorResponse(id, -32602, "Tool not found: " + toolName);
        }

        try {
            String result = tool.execute(arguments);

            Map<String, Object> response = new HashMap<>();
            response.put("content", List.of(
                    Map.of(
                            "type", "text",
                            "text", result
                    )
            ));

            log.debug("Tool {} executed successfully", toolName);
            return createSuccessResponse(id, response);

        } catch (Exception e) {
            log.error("Tool execution error: {}", toolName, e);
            return createErrorResponse(id, -32603, "Tool execution failed: " + e.getMessage());
        }
    }

    private String handlePluginsList(JsonNode request, Object id) throws Exception {
        if (!initialized) {
            return createErrorResponse(id, -32002, "Not initialized");
        }

        List<Map<String, Object>> plugins = new ArrayList<>();

        for (PluginManager.PluginInfo plugin : pluginManager.getAllPlugins()) {
            Map<String, Object> pluginInfo = new HashMap<>();
            pluginInfo.put("name", plugin.getName());
            pluginInfo.put("version", plugin.getVersion());
            pluginInfo.put("description", plugin.getDescription());
            pluginInfo.put("type", plugin.getType());
            pluginInfo.put("toolCount", plugin.getTools().size());
            pluginInfo.put("tools", new ArrayList<>(plugin.getTools().keySet()));
            plugins.add(pluginInfo);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("plugins", plugins);

        log.debug("Listed {} plugins", plugins.size());
        return createSuccessResponse(id, result);
    }

    private String handlePluginsReload(JsonNode request, Object id) throws Exception {
        if (!initialized) {
            return createErrorResponse(id, -32002, "Not initialized");
        }

        log.info("Reloading all plugins...");
        pluginManager.reloadAllPlugins();

        Map<String, Object> result = new HashMap<>();
        result.put("reloaded", pluginManager.getAllPlugins().size());
        result.put("totalTools", getAllTools().size());

        log.info("Reloaded {} plugins with {} total tools", pluginManager.getAllPlugins().size(), getAllTools().size());
        return createSuccessResponse(id, result);
    }

    private String handlePing(JsonNode request, Object id) throws Exception {
        Map<String, Object> result = new HashMap<>();
        result.put("status", "alive");
        result.put("plugins", pluginManager.getAllPlugins().size());
        result.put("tools", getAllTools().size());
        return createSuccessResponse(id, result);
    }

    private String createSuccessResponse(Object id, Object result) throws Exception {
        Map<String, Object> response = new HashMap<>();
        response.put("jsonrpc", "2.0");
        response.put("id", id);
        response.put("result", result);
        return objectMapper.writeValueAsString(response);
    }

    private String createErrorResponse(Object id, int code, String message) {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("jsonrpc", "2.0");
            response.put("id", id);
            response.put("error", Map.of(
                    "code", code,
                    "message", message
            ));
            return objectMapper.writeValueAsString(response);
        } catch (Exception e) {
            log.error("Error creating error response", e);
            return "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32603,\"message\":\"Internal error\"}}";
        }
    }

    @PreDestroy
    public void shutdown() {
        watching = false;

        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                log.error("Error closing watch service", e);
            }
        }

        if (watchThread != null) {
            watchThread.interrupt();
        }

        log.info("PluginBasedMCPServer shut down");
    }
}

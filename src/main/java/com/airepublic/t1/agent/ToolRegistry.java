package com.airepublic.t1.agent;

import com.airepublic.t1.mcp.MCPClient;
import com.airepublic.t1.plugins.PluginManager;
import com.airepublic.t1.tools.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class ToolRegistry {
    // Core tools are now exposed via MCP server, not registered directly
    private final PluginManager pluginManager;
    private final MCPClient mcpClient;

    private final Map<String, AgentTool> tools = new ConcurrentHashMap<>();

    @PostConstruct
    public void initialize() {
        // Core tools are now available via MCP server connection
        log.info("ToolRegistry initialized - tools will be provided via MCP");
    }

    public void registerTool(AgentTool tool) {
        tools.put(tool.getName(), tool);
        log.debug("Registered tool: {}", tool.getName());
    }

    public void unregisterTool(String toolName) {
        tools.remove(toolName);
        log.debug("Unregistered tool: {}", toolName);
    }

    public AgentTool getTool(String name) {
        return tools.get(name);
    }

    public Collection<AgentTool> getAllTools() {
        // Use LinkedHashMap to preserve order and prevent duplicates (by tool name)
        Map<String, AgentTool> uniqueTools = new LinkedHashMap<>();

        // Priority 1: MCP tools from connected servers (including our own local server)
        // This is the PRIMARY source of tools
        mcpClient.getAllMCPTools().forEach(tool ->
            uniqueTools.put(tool.getName(), tool));

        // Priority 2: Plugin tools (only if not already in MCP)
        pluginManager.getAllPluginTools().forEach(tool ->
            uniqueTools.putIfAbsent(tool.getName(), tool));

        // Priority 3: Manually registered tools (lowest priority)
        tools.values().forEach(tool ->
            uniqueTools.putIfAbsent(tool.getName(), tool));

        log.debug("getAllTools() returning {} unique tools", uniqueTools.size());
        return uniqueTools.values();
    }

    public List<String> getToolNames() {
        return new ArrayList<>(tools.keySet());
    }

    public void refreshPluginTools() {
        // Remove old plugin tools
        tools.entrySet().removeIf(entry ->
                entry.getValue().getClass().getName().contains("plugin"));

        // Add new plugin tools
        pluginManager.getAllPluginTools().forEach(tool -> {
            tools.put(tool.getName(), tool);
            log.debug("Registered plugin tool: {}", tool.getName());
        });

        log.info("Refreshed plugin tools. Total tools: {}", tools.size());
    }

    public int getToolCount() {
        return getAllTools().size();
    }
}

package com.airepublic.t1.api.controller;

import com.airepublic.t1.api.dto.ApiResponse;
import com.airepublic.t1.api.dto.MCPServerInfo;
import com.airepublic.t1.mcp.MCPClient;
import com.airepublic.t1.mcp.PluginBasedMCPServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/mcp")
@RequiredArgsConstructor
public class MCPController {
    private final MCPClient mcpClient;
    private final PluginBasedMCPServer pluginMcpServer;

    @GetMapping("/servers")
    public ResponseEntity<ApiResponse<List<MCPServerInfo>>> listServers() {
        try {
            List<MCPServerInfo> servers = mcpClient.getConnectedServers().stream()
                    .map(serverName -> MCPServerInfo.builder()
                            .name(serverName)
                            .connected(true)
                            .build())
                    .collect(Collectors.toList());

            return ResponseEntity.ok(ApiResponse.success(servers));

        } catch (Exception e) {
            log.error("Error listing MCP servers", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Error listing servers: " + e.getMessage()));
        }
    }

    @GetMapping("/servers/{name}/tools")
    public ResponseEntity<ApiResponse<List<MCPServerInfo.MCPToolInfo>>> listTools(@PathVariable String name) {
        try {
            List<MCPServerInfo.MCPToolInfo> tools = mcpClient.listTools(name).stream()
                    .map(tool -> MCPServerInfo.MCPToolInfo.builder()
                            .name(tool.getName())
                            .description(tool.getDescription())
                            .build())
                    .collect(Collectors.toList());

            return ResponseEntity.ok(ApiResponse.success(tools));

        } catch (Exception e) {
            log.error("Error listing tools for server: {}", name, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Error listing tools: " + e.getMessage()));
        }
    }

    @GetMapping("/local/tools")
    public ResponseEntity<ApiResponse<Integer>> getLocalToolCount() {
        try {
            int count = pluginMcpServer.getToolCount();
            return ResponseEntity.ok(ApiResponse.success(count));
        } catch (Exception e) {
            log.error("Error getting local tool count", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Error getting tool count: " + e.getMessage()));
        }
    }

    @PostMapping("/local/reload")
    public ResponseEntity<ApiResponse<String>> reloadPlugins() {
        try {
            // Trigger plugin reload via JSON-RPC
            String request = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"plugins/reload\"}";
            String response = pluginMcpServer.handleRequestExternal(request);
            return ResponseEntity.ok(ApiResponse.success("Plugins reloaded successfully"));
        } catch (Exception e) {
            log.error("Error reloading plugins", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Error reloading plugins: " + e.getMessage()));
        }
    }
}

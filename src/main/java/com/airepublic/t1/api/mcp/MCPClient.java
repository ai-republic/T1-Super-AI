package com.airepublic.t1.mcp;

import com.airepublic.t1.model.AgentConfiguration.MCPServerConfig;
import com.airepublic.t1.tools.AgentTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class MCPClient {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, MCPConnection> connections = new ConcurrentHashMap<>();

    /**
     * Connect to our own MCP server running locally via HTTP.
     * This is mandatory for tool access.
     *
     * @param port The port our MCP server is running on
     * @throws Exception if connection fails
     */
    public void connectToSelf(int port) throws Exception {
        String serverName = "local";
        String baseUrl = "http://localhost:" + port;

        log.info("Connecting to local MCP server at {}", baseUrl);

        // Create a simple HTTP connection
        MCPConnection connection = new MCPConnection(serverName, baseUrl);
        connections.put(serverName, connection);

        // Initialize connection
        sendInitializeHttp(connection);

        // Don't call listTools() here - it would create circular dependency
        // Tools will be retrieved when needed by ToolRegistry.getAllTools()
        log.info("✅ Connected to local MCP server");
    }

    public void connect(MCPServerConfig config) throws Exception {
        log.info("Connecting to MCP server: {}", config.getName());

        if ("stdio".equalsIgnoreCase(config.getTransport())) {
            connectStdio(config);
        } else if ("http".equalsIgnoreCase(config.getTransport())) {
            connectHttp(config);
        } else {
            throw new IllegalArgumentException("Unsupported transport: " + config.getTransport());
        }
    }

    private void connectStdio(MCPServerConfig config) throws Exception {
        ProcessBuilder pb = new ProcessBuilder();
        pb.command().add(config.getCommand());
        pb.command().addAll(config.getArgs());

        if (!config.getEnvironment().isEmpty()) {
            pb.environment().putAll(config.getEnvironment());
        }

        Process process = pb.start();

        MCPConnection connection = new MCPConnection(
                config.getName(),
                process.getOutputStream(),
                process.getInputStream()
        );

        connections.put(config.getName(), connection);

        // Initialize connection
        sendInitialize(connection);

        log.info("Connected to MCP server via stdio: {}", config.getName());
    }

    private void connectHttp(MCPServerConfig config) throws Exception {
        // HTTP/SSE connection implementation
        log.warn("HTTP transport not yet implemented for MCP server: {}", config.getName());
    }

    private void sendInitialize(MCPConnection connection) throws IOException {
        Map<String, Object> request = new HashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("id", 1);
        request.put("method", "initialize");

        Map<String, Object> params = new HashMap<>();
        params.put("protocolVersion", "2024-11-05");
        params.put("clientInfo", Map.of(
                "name", "t1-super-ai",
                "version", "1.0.0"
        ));
        params.put("capabilities", Map.of(
                "tools", Map.of()
        ));

        request.put("params", params);

        connection.send(objectMapper.writeValueAsString(request));

        // Read response
        String response = connection.receive();
        log.debug("Initialize response: {}", response);
    }

    public List<MCPTool> listTools(String serverName) throws Exception {
        MCPConnection connection = connections.get(serverName);
        if (connection == null) {
            throw new IllegalStateException("Not connected to server: " + serverName);
        }

        Map<String, Object> request = new HashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("id", 2);
        request.put("method", "tools/list");

        String response;
        if (connection.httpBaseUrl != null) {
            response = connection.sendHttp(objectMapper.writeValueAsString(request));
        } else {
            connection.send(objectMapper.writeValueAsString(request));
            response = connection.receive();
        }

        JsonNode responseNode = objectMapper.readTree(response);
        JsonNode tools = responseNode.get("result").get("tools");

        List<MCPTool> toolList = new ArrayList<>();
        if (tools.isArray()) {
            for (JsonNode toolNode : tools) {
                MCPTool tool = new MCPTool();
                tool.setName(toolNode.get("name").asText());
                tool.setDescription(toolNode.get("description").asText());
                tool.setInputSchema(objectMapper.convertValue(
                        toolNode.get("inputSchema"),
                        Map.class
                ));
                toolList.add(tool);
            }
        }

        log.info("Listed {} tools from MCP server: {}", toolList.size(), serverName);
        return toolList;
    }

    public String callTool(String serverName, String toolName, Map<String, Object> arguments)
            throws Exception {
        MCPConnection connection = connections.get(serverName);
        if (connection == null) {
            throw new IllegalStateException("Not connected to server: " + serverName);
        }

        Map<String, Object> request = new HashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("id", System.currentTimeMillis());
        request.put("method", "tools/call");

        Map<String, Object> params = new HashMap<>();
        params.put("name", toolName);
        params.put("arguments", arguments);
        request.put("params", params);

        String response;
        if (connection.httpBaseUrl != null) {
            response = connection.sendHttp(objectMapper.writeValueAsString(request));
        } else {
            connection.send(objectMapper.writeValueAsString(request));
            response = connection.receive();
        }

        JsonNode responseNode = objectMapper.readTree(response);

        if (responseNode.has("error")) {
            throw new Exception("MCP tool error: " + responseNode.get("error"));
        }

        JsonNode result = responseNode.get("result");
        return objectMapper.writeValueAsString(result);
    }

    public void disconnect(String serverName) {
        MCPConnection connection = connections.remove(serverName);
        if (connection != null) {
            connection.close();
            log.info("Disconnected from MCP server: {}", serverName);
        }
    }

    public void disconnectAll() {
        connections.keySet().forEach(this::disconnect);
    }

    public Set<String> getConnectedServers() {
        return connections.keySet();
    }

    /**
     * Gets all tools from all connected MCP servers as AgentTools.
     * This allows MCP tools to be integrated with the agent's tool registry.
     *
     * @return List of AgentTool wrappers for MCP tools
     */
    public List<AgentTool> getAllMCPTools() {
        List<AgentTool> mcpTools = new ArrayList<>();

        for (String serverName : connections.keySet()) {
            try {
                List<MCPTool> tools = listTools(serverName);
                for (MCPTool mcpTool : tools) {
                    // Wrap MCPTool as AgentTool
                    mcpTools.add(new AgentTool() {
                        @Override
                        public String getName() {
                            // Don't prefix local server tools
                            if ("local".equals(serverName)) {
                                return mcpTool.getName();
                            }
                            return serverName + ":" + mcpTool.getName();
                        }

                        @Override
                        public String getDescription() {
                            if ("local".equals(serverName)) {
                                return mcpTool.getDescription();
                            }
                            return mcpTool.getDescription() + " (from MCP server: " + serverName + ")";
                        }

                        @Override
                        public Map<String, Object> getParameterSchema() {
                            return mcpTool.getInputSchema();
                        }

                        @Override
                        public String execute(Map<String, Object> arguments) throws Exception {
                            return callTool(serverName, mcpTool.getName(), arguments);
                        }
                    });
                }
            } catch (Exception e) {
                log.error("Failed to list tools from MCP server: {}", serverName, e);
            }
        }

        log.debug("Retrieved {} MCP tools from {} servers", mcpTools.size(), connections.size());
        return mcpTools;
    }

    private void sendInitializeHttp(MCPConnection connection) throws IOException {
        Map<String, Object> request = new HashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("id", 1);
        request.put("method", "initialize");

        Map<String, Object> params = new HashMap<>();
        params.put("protocolVersion", "2024-11-05");
        params.put("clientInfo", Map.of(
                "name", "t1-super-ai",
                "version", "1.0.0"
        ));
        params.put("capabilities", Map.of(
                "tools", Map.of()
        ));

        request.put("params", params);

        String response = connection.sendHttp(objectMapper.writeValueAsString(request));
        log.debug("Initialize response: {}", response);
    }

    private static class MCPConnection {
        private final String name;
        private final OutputStream outputStream;
        private final BufferedReader inputReader;
        private final String httpBaseUrl;  // For HTTP transport

        // Constructor for stdio transport
        public MCPConnection(String name, OutputStream outputStream, InputStream inputStream) {
            this.name = name;
            this.outputStream = outputStream;
            this.inputReader = new BufferedReader(new InputStreamReader(inputStream));
            this.httpBaseUrl = null;
        }

        // Constructor for HTTP transport
        public MCPConnection(String name, String httpBaseUrl) {
            this.name = name;
            this.httpBaseUrl = httpBaseUrl;
            this.outputStream = null;
            this.inputReader = null;
        }

        public void send(String message) throws IOException {
            if (httpBaseUrl != null) {
                throw new UnsupportedOperationException("Use sendHttp for HTTP connections");
            }
            outputStream.write(message.getBytes());
            outputStream.write('\n');
            outputStream.flush();
        }

        public String sendHttp(String jsonRequest) throws IOException {
            if (httpBaseUrl == null) {
                throw new UnsupportedOperationException("This is not an HTTP connection");
            }

            java.net.URL url = new java.net.URL(httpBaseUrl);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            try (java.io.OutputStream os = conn.getOutputStream()) {
                os.write(jsonRequest.getBytes());
                os.flush();
            }

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                throw new IOException("HTTP error: " + responseCode);
            }

            try (java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.InputStreamReader(conn.getInputStream()))) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line);
                }
                return response.toString();
            }
        }

        public String receive() throws IOException {
            if (httpBaseUrl != null) {
                throw new UnsupportedOperationException("Use sendHttp for HTTP connections");
            }
            return inputReader.readLine();
        }

        public void close() {
            try {
                if (outputStream != null) {
                    outputStream.close();
                }
                if (inputReader != null) {
                    inputReader.close();
                }
            } catch (IOException e) {
                log.error("Error closing MCP connection: {}", name, e);
            }
        }
    }
}

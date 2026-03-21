package com.airepublic.t1.mcp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.ServerSocket;

/**
 * Automatically starts MCP server in background when CLI application starts.
 *
 * The MCP server runs on a separate port and can be accessed by MCP clients
 * while the CLI is running.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MCPServerRunner {

    private final PluginBasedMCPServer mcpServer;
    private ServerSocket serverSocket;
    private Thread mcpServerThread;
    private int actualPort = -1;
    private static final int DEFAULT_MCP_PORT = 3000;
    private static final int MAX_PORT_ATTEMPTS = 10;

    public int startMCPServerInBackground() {
        mcpServerThread = new Thread(() -> {
            try {
                log.info("🚀 Starting MCP Server in background mode...");
                // Don't call getToolCount() here - it triggers MCP client connection before server is ready
                log.info("🔧 MCP Server starting - tools will be available after startup");

                // Try to find an available port
                int port = findAvailablePort(DEFAULT_MCP_PORT);
                if (port == -1) {
                    log.warn("⚠️  Could not find available port for MCP server");
                    log.info("💡 MCP server not available. You can still use the CLI normally.");
                    return;
                }

                // Create server socket for HTTP/JSON-RPC transport
                serverSocket = new ServerSocket(port);
                actualPort = port;
                log.info("📡 MCP Server listening on port {}", port);

                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        var clientSocket = serverSocket.accept();
                        log.debug("📞 MCP client connected from {}", clientSocket.getRemoteSocketAddress());

                        // Handle client in separate thread
                        Thread clientThread = new Thread(() -> handleMCPClient(clientSocket));
                        clientThread.setDaemon(true);
                        clientThread.start();

                    } catch (IOException e) {
                        if (!serverSocket.isClosed()) {
                            log.error("Error accepting MCP client connection", e);
                        }
                    }
                }
            } catch (IOException e) {
                log.error("Failed to start MCP server", e);
                log.info("💡 MCP server not available. You can still use the CLI normally.");
            }
        }, "mcp-server-background");

        mcpServerThread.setDaemon(true);
        mcpServerThread.start();

        // Wait a moment for the server to start
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        log.info("✅ MCP Server started in background");
        log.info("💡 Other applications can now connect to this agent via MCP on port 3000-3010");

        return actualPort;
    }

    private int findAvailablePort(int startPort) {
        for (int port = startPort; port < startPort + MAX_PORT_ATTEMPTS; port++) {
            try {
                ServerSocket testSocket = new ServerSocket(port);
                testSocket.close();
                return port;
            } catch (IOException e) {
                // Port in use, try next
            }
        }
        return -1;
    }

    private void handleMCPClient(java.net.Socket clientSocket) {
        try (var input = new java.io.BufferedReader(
                new java.io.InputStreamReader(clientSocket.getInputStream()));
             var output = new java.io.PrintWriter(clientSocket.getOutputStream(), true)) {

            // Read HTTP request line
            String requestLine = input.readLine();
            if (requestLine == null || !requestLine.startsWith("POST")) {
                return;
            }

            // Read HTTP headers and find content length
            int contentLength = 0;
            String line;
            while ((line = input.readLine()) != null && !line.trim().isEmpty()) {
                if (line.startsWith("Content-Length:")) {
                    contentLength = Integer.parseInt(line.substring(15).trim());
                }
            }

            // Read request body
            char[] body = new char[contentLength];
            input.read(body, 0, contentLength);
            String jsonRequest = new String(body);

            try {
                // Process MCP request using the existing MCPServer
                String jsonResponse = mcpServer.handleRequestExternal(jsonRequest);

                // Send HTTP response
                output.println("HTTP/1.1 200 OK");
                output.println("Content-Type: application/json");
                output.println("Content-Length: " + jsonResponse.length());
                output.println();
                output.println(jsonResponse);
                output.flush();

            } catch (Exception e) {
                log.error("Error processing MCP request", e);
                String errorResponse = "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32603,\"message\":\"Internal error\"}}";
                output.println("HTTP/1.1 200 OK");
                output.println("Content-Type: application/json");
                output.println("Content-Length: " + errorResponse.length());
                output.println();
                output.println(errorResponse);
                output.flush();
            }

        } catch (IOException e) {
            log.debug("MCP client disconnected: {}", e.getMessage());
        }
    }

    public void shutdown() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            if (mcpServerThread != null) {
                mcpServerThread.interrupt();
            }
            log.info("🛑 MCP Server stopped");
        } catch (IOException e) {
            log.error("Error stopping MCP server", e);
        }
    }
}

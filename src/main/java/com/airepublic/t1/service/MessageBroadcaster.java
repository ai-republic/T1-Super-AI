package com.airepublic.t1.service;

import com.airepublic.t1.api.dto.CollaborationMessageDTO;
import com.airepublic.t1.api.websocket.ChatWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Service for broadcasting agent collaboration messages to UI clients.
 * Supports both WebSocket (Web UI) and callback-based (CLI) output.
 */
@Slf4j
@Service
public class MessageBroadcaster {

    private final ObjectMapper objectMapper;
    private ChatWebSocketHandler webSocketHandler;
    private Consumer<String> cliOutputCallback;

    public MessageBroadcaster() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * Set the WebSocket handler for web UI broadcasting.
     */
    public void setWebSocketHandler(ChatWebSocketHandler handler) {
        this.webSocketHandler = handler;
    }

    /**
     * Set the CLI output callback for terminal output.
     */
    public void setCliOutputCallback(Consumer<String> callback) {
        this.cliOutputCallback = callback;
    }

    /**
     * Broadcast a tool call event.
     */
    public void broadcastToolCall(String agentName, String toolName, Map<String, Object> arguments) {
        try {
            CollaborationMessageDTO message = CollaborationMessageDTO.builder()
                    .type("tool_call")
                    .fromAgent(agentName)
                    .toolName(toolName)
                    .toolArguments(arguments)
                    .timestamp(LocalDateTime.now())
                    .build();

            broadcast(message);
        } catch (Exception e) {
            log.error("Error broadcasting tool call", e);
        }
    }

    /**
     * Broadcast a tool result event.
     */
    public void broadcastToolResult(String agentName, String toolName, String result, boolean success, long executionTimeMs) {
        try {
            CollaborationMessageDTO message = CollaborationMessageDTO.builder()
                    .type("tool_result")
                    .fromAgent(agentName)
                    .toolName(toolName)
                    .toolResult(result)
                    .success(success)
                    .responseTimeMs(executionTimeMs)
                    .timestamp(LocalDateTime.now())
                    .build();

            broadcast(message);
        } catch (Exception e) {
            log.error("Error broadcasting tool result", e);
        }
    }

    /**
     * Broadcast an agent-to-agent communication event.
     */
    public void broadcastAgentCommunication(String fromAgent, String toAgent, String message) {
        try {
            CollaborationMessageDTO dto = CollaborationMessageDTO.builder()
                    .type("agent_communication")
                    .fromAgent(fromAgent)
                    .toAgent(toAgent)
                    .message(message)
                    .timestamp(LocalDateTime.now())
                    .build();

            broadcast(dto);
        } catch (Exception e) {
            log.error("Error broadcasting agent communication", e);
        }
    }

    /**
     * Broadcast an agent response event.
     */
    public void broadcastAgentResponse(String fromAgent, String toAgent, String response, long responseTimeMs) {
        try {
            CollaborationMessageDTO dto = CollaborationMessageDTO.builder()
                    .type("agent_response")
                    .fromAgent(fromAgent)
                    .toAgent(toAgent)
                    .response(response)
                    .responseTimeMs(responseTimeMs)
                    .timestamp(LocalDateTime.now())
                    .build();

            broadcast(dto);
        } catch (Exception e) {
            log.error("Error broadcasting agent response", e);
        }
    }

    /**
     * Internal method to broadcast messages to all registered outputs.
     */
    private void broadcast(CollaborationMessageDTO message) {
        try {
            String jsonMessage = objectMapper.writeValueAsString(message);

            // Broadcast to WebSocket clients (Web UI)
            if (webSocketHandler != null) {
                webSocketHandler.broadcastCollaborationMessage(jsonMessage);
            }

            // Broadcast to CLI if callback is set
            if (cliOutputCallback != null) {
                String formattedMessage = formatForCLI(message);
                cliOutputCallback.accept(formattedMessage);
            }
        } catch (Exception e) {
            log.error("Error broadcasting message", e);
        }
    }

    /**
     * Format collaboration message for CLI display.
     */
    private String formatForCLI(CollaborationMessageDTO message) {
        switch (message.getType()) {
            case "tool_call":
                return String.format("🔧 [%s] Calling tool: %s",
                    message.getFromAgent(), message.getToolName());

            case "tool_result":
                String status = Boolean.TRUE.equals(message.getSuccess()) ? "✓" : "✗";
                return String.format("  %s Result: %s (took %dms)",
                    status,
                    truncate(message.getToolResult(), 100),
                    message.getResponseTimeMs());

            case "agent_communication":
                return String.format("🔄 [%s → %s]: %s",
                    message.getFromAgent(),
                    message.getToAgent(),
                    truncate(message.getMessage(), 100));

            case "agent_response":
                return String.format("📥 [%s → %s]: %s (took %dms)",
                    message.getFromAgent(),
                    message.getToAgent(),
                    truncate(message.getResponse(), 100),
                    message.getResponseTimeMs());

            default:
                return String.format("ℹ️  Collaboration event: %s", message.getType());
        }
    }

    /**
     * Truncate string for CLI display.
     */
    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }
}

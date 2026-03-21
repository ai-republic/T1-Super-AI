package com.airepublic.t1.api.websocket;

import com.airepublic.t1.agent.AgentManager;
import com.airepublic.t1.agent.AgentOrchestrator;
import com.airepublic.t1.config.AgentConfigurationManager;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatWebSocketHandler extends TextWebSocketHandler {
    private final AgentOrchestrator orchestrator;
    private final AgentManager agentManager;
    private final AgentConfigurationManager configManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Track sessions and their associated agents
    private final Map<String, String> sessionAgents = new ConcurrentHashMap<>();

    // Track all active WebSocket sessions
    private final List<WebSocketSession> activeSessions = new ArrayList<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        log.info("WebSocket connection established: {}", session.getId());

        // Add to active sessions
        synchronized (activeSessions) {
            activeSessions.add(session);
        }

        // Send welcome message
        Map<String, Object> welcome = Map.of(
                "type", "connected",
                "message", "Connected to T1 Super AI",
                "sessionId", session.getId(),
                "timestamp", LocalDateTime.now().toString()
        );
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(welcome)));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            String payload = message.getPayload();
            log.debug("Received WebSocket message: {}", payload);

            // Parse message
            JsonNode jsonNode = objectMapper.readTree(payload);
            String type = jsonNode.has("type") ? jsonNode.get("type").asText() : "chat";
            String userMessage = jsonNode.has("message") ? jsonNode.get("message").asText() : "";
            String agentName = jsonNode.has("agentName") ? jsonNode.get("agentName").asText() : null;

            switch (type) {
                case "chat" -> handleChatMessage(session, userMessage, agentName);
                case "switchAgent" -> handleSwitchAgent(session, agentName);
                case "clearHistory" -> handleClearHistory(session);
                case "ping" -> handlePing(session);
                default -> {
                    sendError(session, "Unknown message type: " + type);
                }
            }

        } catch (Exception e) {
            log.error("Error handling WebSocket message", e);
            sendError(session, "Error processing message: " + e.getMessage());
        }
    }

    private void handleChatMessage(WebSocketSession session, String userMessage, String agentName) throws Exception {
        // Send processing status
        sendStatus(session, "processing", "Processing your message...");

        try {
            // Switch agent if specified
            if (agentName != null && !agentName.isEmpty()) {
                String currentAgent = sessionAgents.get(session.getId());
                if (currentAgent == null || !currentAgent.equals(agentName)) {
                    agentManager.switchToAgent(agentName);
                    sessionAgents.put(session.getId(), agentName);
                }
            }

            // Get current agent
            String currentAgentName = agentManager.getCurrentAgentName();
            sessionAgents.putIfAbsent(session.getId(), currentAgentName);

            long startTime = System.currentTimeMillis();

            // Process message
            String response = orchestrator.processMessage(userMessage);

            long responseTime = System.currentTimeMillis() - startTime;

            // Send response
            Map<String, Object> responseData = Map.of(
                    "type", "response",
                    "message", response,
                    "agentName", currentAgentName,
                    "modelUsed", configManager.getCurrentModel(),
                    "responseTimeMs", responseTime,
                    "timestamp", LocalDateTime.now().toString()
            );

            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(responseData)));

        } catch (Exception e) {
            log.error("Error processing chat message", e);
            sendError(session, "Error processing message: " + e.getMessage());
        }
    }

    private void handleSwitchAgent(WebSocketSession session, String agentName) throws Exception {
        try {
            if (agentName == null || agentName.trim().isEmpty()) {
                sendError(session, "Agent name is required");
                return;
            }

            if (!agentManager.hasAgent(agentName)) {
                sendError(session, "Agent not found: " + agentName);
                return;
            }

            agentManager.switchToAgent(agentName);
            sessionAgents.put(session.getId(), agentName);

            Map<String, Object> response = Map.of(
                    "type", "agentSwitched",
                    "agentName", agentName,
                    "message", "Switched to agent: " + agentName,
                    "timestamp", LocalDateTime.now().toString()
            );

            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));

        } catch (Exception e) {
            log.error("Error switching agent", e);
            sendError(session, "Error switching agent: " + e.getMessage());
        }
    }

    private void handleClearHistory(WebSocketSession session) throws Exception {
        try {
            orchestrator.clearHistory();

            Map<String, Object> response = Map.of(
                    "type", "historyCleared",
                    "message", "Conversation history cleared",
                    "timestamp", LocalDateTime.now().toString()
            );

            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));

        } catch (Exception e) {
            log.error("Error clearing history", e);
            sendError(session, "Error clearing history: " + e.getMessage());
        }
    }

    private void handlePing(WebSocketSession session) throws Exception {
        Map<String, Object> pong = Map.of(
                "type", "pong",
                "timestamp", LocalDateTime.now().toString()
        );
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(pong)));
    }

    private void sendStatus(WebSocketSession session, String status, String message) throws Exception {
        Map<String, Object> statusMessage = Map.of(
                "type", "status",
                "status", status,
                "message", message,
                "timestamp", LocalDateTime.now().toString()
        );
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(statusMessage)));
    }

    private void sendError(WebSocketSession session, String errorMessage) throws Exception {
        Map<String, Object> error = Map.of(
                "type", "error",
                "message", errorMessage,
                "timestamp", LocalDateTime.now().toString()
        );
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(error)));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        log.info("WebSocket connection closed: {} with status: {}", session.getId(), status);
        sessionAgents.remove(session.getId());

        // Remove from active sessions
        synchronized (activeSessions) {
            activeSessions.remove(session);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("WebSocket transport error for session: {}", session.getId(), exception);
        sessionAgents.remove(session.getId());

        // Remove from active sessions
        synchronized (activeSessions) {
            activeSessions.remove(session);
        }
    }

    /**
     * Broadcast a collaboration message to all active WebSocket clients.
     * Called by MessageBroadcaster service.
     */
    public void broadcastCollaborationMessage(String jsonMessage) {
        List<WebSocketSession> sessionsToRemove = new ArrayList<>();

        synchronized (activeSessions) {
            for (WebSocketSession session : activeSessions) {
                try {
                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage(jsonMessage));
                    } else {
                        sessionsToRemove.add(session);
                    }
                } catch (IOException e) {
                    log.error("Error broadcasting to session {}: {}", session.getId(), e.getMessage());
                    sessionsToRemove.add(session);
                }
            }

            // Clean up closed sessions
            activeSessions.removeAll(sessionsToRemove);
        }

        if (!sessionsToRemove.isEmpty()) {
            log.debug("Removed {} closed sessions during broadcast", sessionsToRemove.size());
        }
    }
}

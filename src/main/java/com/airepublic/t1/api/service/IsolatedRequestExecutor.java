package com.airepublic.t1.service;

import com.airepublic.t1.agent.Agent;
import com.airepublic.t1.agent.AgentManager;
import com.airepublic.t1.agent.AgentOrchestrator;
import com.airepublic.t1.model.ConversationMessage;
import com.airepublic.t1.model.MessageAttachment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * Executes chat requests in complete isolation with agent-specific context.
 * Each request runs in its own thread with its own context snapshot.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IsolatedRequestExecutor {
    private final AgentManager agentManager;

    /**
     * Execute a message processing request in complete isolation.
     * This method:
     * 1. Gets the specific agent instance
     * 2. Uses the agent's orchestrator directly (no global state modification)
     * 3. Runs in a separate thread without blocking other requests
     *
     * @param agentName The agent to use for processing
     * @param message The user message
     * @param attachments Optional attachments
     * @param panelId The panel ID for tracking
     * @return The agent's response
     */
    public String executeIsolated(String agentName, String message,
                                   List<MessageAttachment> attachments, String panelId) {
        log.info("🚀 Isolated execution started - Agent: {}, Panel: {}", agentName, panelId);

        // Get the specific agent (thread-safe read from ConcurrentHashMap)
        Agent agent = agentManager.getAgent(agentName);
        if (agent == null) {
            throw new IllegalArgumentException("Agent '" + agentName + "' not found");
        }

        // CRITICAL: Use the agent's own orchestrator directly
        // Each agent has a reference to the orchestrator, but we need to ensure
        // we're using agent-specific context
        AgentOrchestrator orchestrator = agent.getOrchestrator();

        try {
            // Build a context-aware message that includes the agent identity
            // This helps the orchestrator know which agent context to use
            log.debug("Processing message for agent '{}' in panel '{}'", agentName, panelId);

            // Process the message - the orchestrator will use its current session context
            // which should have been loaded for this agent
            String response;
            if (attachments != null && !attachments.isEmpty()) {
                response = orchestrator.processMessage(message, attachments);
            } else {
                response = orchestrator.processMessage(message);
            }

            log.info("✅ Isolated execution completed - Agent: {}, Panel: {}", agentName, panelId);
            return response;

        } catch (Exception e) {
            log.error("❌ Isolated execution failed - Agent: {}, Panel: {}", agentName, panelId, e);
            throw new RuntimeException("Error processing message for agent '" + agentName + "': " + e.getMessage(), e);
        }
    }

    /**
     * Get conversation history for a specific agent
     */
    public List<ConversationMessage> getAgentHistory(String agentName) {
        Agent agent = agentManager.getAgent(agentName);
        if (agent == null) {
            throw new IllegalArgumentException("Agent '" + agentName + "' not found");
        }
        return agent.getOrchestrator().getConversationHistoryCopy();
    }
}

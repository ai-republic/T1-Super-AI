package com.airepublic.t1.agent;

import com.airepublic.t1.model.ConversationMessage;
import lombok.Data;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Represents an isolated execution context for a single agent request.
 * Each request gets its own context with independent conversation history and session context.
 */
@Data
public class AgentExecutionContext {
    private final String agentName;
    private final String panelId;
    private final String requestId;
    private final List<ConversationMessage> conversationHistory;
    private String sessionContext;

    public AgentExecutionContext(String agentName, String panelId, String requestId) {
        this.agentName = agentName;
        this.panelId = panelId;
        this.requestId = requestId;
        this.conversationHistory = new CopyOnWriteArrayList<>();
        this.sessionContext = null;
    }

    /**
     * Copy constructor for creating a new context based on existing conversation history
     */
    public AgentExecutionContext(String agentName, String panelId, String requestId,
                                   List<ConversationMessage> existingHistory, String sessionContext) {
        this.agentName = agentName;
        this.panelId = panelId;
        this.requestId = requestId;
        this.conversationHistory = new CopyOnWriteArrayList<>(existingHistory);
        this.sessionContext = sessionContext;
    }
}

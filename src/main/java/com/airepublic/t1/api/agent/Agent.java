package com.airepublic.t1.agent;

import com.airepublic.t1.model.IndividualAgentConfig;
import lombok.Data;
import org.springframework.ai.chat.model.ChatModel;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Represents an agent instance with its own session context and conversation history.
 * Each agent can run in its own thread and maintain independent state.
 */
@Data
public class Agent {
    private String name;
    private String status; // "active", "idle", "stopped"
    private LocalDateTime createdAt;
    private LocalDateTime lastActiveAt;

    // Agent's own session controller
    private AgentOrchestrator orchestrator;

    // Agent's conversation history
    private List<ConversationEntry> conversationHistory;

    // Agent-specific configuration (role, context, provider, model)
    private IndividualAgentConfig config;

    // Thread for running this agent
    private Thread agentThread;

    // Input queue for messages
    private BlockingQueue<String> inputQueue;

    // Output queue for responses
    private BlockingQueue<String> outputQueue;

    // Flag to control agent execution
    private volatile boolean running;

    public Agent(String name, AgentOrchestrator orchestrator) {
        this.name = name;
        this.orchestrator = orchestrator;
        this.status = "active";
        this.createdAt = LocalDateTime.now();
        this.lastActiveAt = LocalDateTime.now();
        this.conversationHistory = new ArrayList<>();
        this.inputQueue = new LinkedBlockingQueue<>();
        this.outputQueue = new LinkedBlockingQueue<>();
        this.running = false;
    }

    public Agent(String name, AgentOrchestrator orchestrator, IndividualAgentConfig config) {
        this(name, orchestrator);
        this.config = config;
    }

    /**
     * Start the agent in its own thread
     */
    public void start() {
        if (agentThread != null && agentThread.isAlive()) {
            throw new IllegalStateException("Agent is already running");
        }

        running = true;
        agentThread = new Thread(this::run, "Agent-" + name);
        agentThread.start();
        status = "active";
    }

    /**
     * Stop the agent thread
     */
    public void stop() {
        running = false;
        status = "stopped";
        if (agentThread != null) {
            agentThread.interrupt();
        }
    }

    /**
     * Main agent execution loop
     */
    private void run() {
        while (running) {
            try {
                // Wait for input message (blocking)
                String input = inputQueue.take();

                // Update last active time
                lastActiveAt = LocalDateTime.now();

                // Process message through orchestrator
                String response = orchestrator.processMessage(input);

                // Add to conversation history
                conversationHistory.add(new ConversationEntry(input, response, LocalDateTime.now()));

                // Put response in output queue
                outputQueue.put(response);

            } catch (InterruptedException e) {
                // Thread interrupted, check if we should stop
                if (!running) {
                    break;
                }
            } catch (Exception e) {
                // Log error but keep running
                String errorResponse = "Error processing message: " + e.getMessage();
                try {
                    outputQueue.put(errorResponse);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * Send a message to this agent
     */
    public void sendMessage(String message) throws InterruptedException {
        inputQueue.put(message);
    }

    /**
     * Get the next response from this agent (blocking)
     */
    public String getResponse() throws InterruptedException {
        return outputQueue.take();
    }

    /**
     * Get the next response from this agent with timeout (non-blocking)
     */
    public String getResponse(long timeoutMs) throws InterruptedException {
        return outputQueue.poll(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * Check if this agent is currently running
     */
    public boolean isRunning() {
        return running && agentThread != null && agentThread.isAlive();
    }

    /**
     * Represents a conversation entry
     */
    @Data
    public static class ConversationEntry {
        private String input;
        private String response;
        private LocalDateTime timestamp;

        public ConversationEntry(String input, String response, LocalDateTime timestamp) {
            this.input = input;
            this.response = response;
            this.timestamp = timestamp;
        }
    }
}

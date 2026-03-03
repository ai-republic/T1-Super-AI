package com.airepublic.t1.agent;

import com.airepublic.t1.agent.ToolRegistry;
import com.airepublic.t1.config.AgentConfigService;
import com.airepublic.t1.config.AgentConfigurationManager;
import com.airepublic.t1.model.IndividualAgentConfig;
import com.airepublic.t1.session.SessionContextManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages multiple agent instances with thread-safe operations.
 * Each agent runs in its own thread with independent session context.
 */
@Slf4j
@Service
public class AgentManager {
    private final Map<String, Agent> agents = new ConcurrentHashMap<>();
    private final ToolRegistry toolRegistry;
    private final AgentConfigurationManager configManager;
    private final AgentConfigService agentConfigService;
    private final SessionContextManager sessionContextManager;
    private final LLMClientFactory llmClientFactory;

    private String currentAgentName = "master";
    private ChatModel chatModel;

    public AgentManager(
            ToolRegistry toolRegistry,
            AgentConfigurationManager configManager,
            AgentConfigService agentConfigService,
            SessionContextManager sessionContextManager,
            LLMClientFactory llmClientFactory) {
        this.toolRegistry = toolRegistry;
        this.configManager = configManager;
        this.agentConfigService = agentConfigService;
        this.sessionContextManager = sessionContextManager;
        this.llmClientFactory = llmClientFactory;
    }

    /**
     * Set the ChatModel for creating agents
     */
    public void setChatModel(ChatModel chatModel) {
        this.chatModel = chatModel;
        log.info("ChatModel configured for AgentManager");
    }

    /**
     * Initialize the master agent
     */
    public void initializeMasterAgent(AgentOrchestrator orchestrator) {
        if (!agents.containsKey("master")) {
            Agent masterAgent = new Agent("master", orchestrator);
            masterAgent.setStatus("active");
            agents.put("master", masterAgent);
            log.info("Master agent initialized");
        }
    }

    /**
     * Load all saved agent configurations from disk and create agent instances.
     * This should be called after the master agent is initialized.
     */
    public void loadSavedAgents(AgentOrchestrator orchestrator) {
        log.info("Loading saved agent configurations...");

        List<IndividualAgentConfig> savedConfigs = agentConfigService.listAllAgentConfigs();

        if (savedConfigs.isEmpty()) {
            log.info("No saved agent configurations found");
            return;
        }

        int loadedCount = 0;
        for (IndividualAgentConfig config : savedConfigs) {
            try {
                String agentName = config.getName();

                // Skip if agent already exists (e.g., master)
                if (agents.containsKey(agentName)) {
                    log.debug("Agent '{}' already exists in memory, skipping", agentName);
                    continue;
                }

                // Create agent with the saved configuration
                Agent agent = new Agent(agentName, orchestrator, config);
                agent.setStatus("idle"); // Start as idle since they're loaded from disk

                // Register the agent
                agents.put(agentName, agent);
                loadedCount++;

                log.info("Loaded agent '{}' with provider: {}, model: {}",
                    agentName, config.getProvider(), config.getModel());

            } catch (Exception e) {
                log.error("Failed to load agent configuration: {}", config.getName(), e);
            }
        }

        log.info("Successfully loaded {} agent(s) from disk", loadedCount);
    }

    /**
     * Create a new agent by forking the current session context
     * Note: Agents share the same orchestrator instance for now,
     * but run in separate threads with independent conversation histories
     */
    public Agent createAgent(String name, AgentOrchestrator sharedOrchestrator) {
        return createAgent(name, sharedOrchestrator, null);
    }

    /**
     * Create a new agent with specific configuration
     */
    public Agent createAgent(String name, AgentOrchestrator sharedOrchestrator, IndividualAgentConfig config) {
        if (agents.containsKey(name)) {
            throw new IllegalArgumentException("Agent '" + name + "' already exists");
        }

        // Get current agent to fork from
        Agent currentAgent = agents.get(currentAgentName);
        if (currentAgent == null) {
            throw new IllegalStateException("Current agent '" + currentAgentName + "' not found");
        }

        // Create new agent with shared orchestrator and config
        Agent newAgent = new Agent(name, sharedOrchestrator, config);

        // Copy conversation history from current agent
        if (currentAgent.getConversationHistory() != null) {
            List<Agent.ConversationEntry> copiedHistory = new ArrayList<>(
                currentAgent.getConversationHistory()
            );
            newAgent.setConversationHistory(copiedHistory);
            log.info("Forked {} conversation entries to new agent '{}'",
                copiedHistory.size(), name);
        }

        // Start the agent thread
        newAgent.start();

        // Register agent
        agents.put(name, newAgent);

        log.info("Created and started new agent: {} with config: {}", name, config != null ? "custom" : "default");
        return newAgent;
    }

    /**
     * Get an agent by name
     */
    public Agent getAgent(String name) {
        return agents.get(name);
    }

    /**
     * Get the current active agent
     */
    public Agent getCurrentAgent() {
        return agents.get(currentAgentName);
    }

    /**
     * Switch to a different agent and reload its session context
     */
    public void switchToAgent(String name) {
        if (!agents.containsKey(name)) {
            throw new IllegalArgumentException("Agent '" + name + "' does not exist");
        }

        String previousAgent = currentAgentName;
        currentAgentName = name;

        // Get the agent's orchestrator and reload its session context
        Agent agent = agents.get(name);
        if (agent != null && agent.getOrchestrator() != null) {
            agent.getOrchestrator().reloadSessionContext(name);
            log.info("Switched to agent '{}' and reloaded its session context", name);
        } else {
            log.info("Switched to agent: {}", name);
        }
    }

    /**
     * Get current agent name
     */
    public String getCurrentAgentName() {
        return currentAgentName;
    }

    /**
     * List all agents
     */
    public List<Agent> listAgents() {
        return new ArrayList<>(agents.values());
    }

    /**
     * Remove an agent
     */
    public boolean removeAgent(String name) {
        if ("master".equals(name)) {
            throw new IllegalArgumentException("Cannot remove the master agent");
        }

        Agent agent = agents.remove(name);
        if (agent != null) {
            // Stop the agent thread
            agent.stop();

            // If this was the current agent, switch to master
            if (name.equals(currentAgentName)) {
                currentAgentName = "master";
                log.info("Switched back to master agent");
            }

            log.info("Removed agent: {}", name);
            return true;
        }
        return false;
    }

    /**
     * Check if an agent exists
     */
    public boolean hasAgent(String name) {
        return agents.containsKey(name);
    }

    /**
     * Get count of agents
     */
    public int getAgentCount() {
        return agents.size();
    }

    /**
     * Update an agent's configuration
     */
    public Agent updateAgentConfig(String name, IndividualAgentConfig newConfig) {
        Agent agent = agents.get(name);
        if (agent == null) {
            throw new IllegalArgumentException("Agent '" + name + "' does not exist");
        }

        // Update the agent's config
        agent.setConfig(newConfig);

        // If this is the current agent, reload its session context
        if (name.equals(currentAgentName) && agent.getOrchestrator() != null) {
            agent.getOrchestrator().reloadSessionContext(name);
            log.info("Reloaded session context for agent '{}' after config update", name);
        }

        log.info("Updated configuration for agent: {}", name);
        return agent;
    }

    /**
     * Shutdown all agents
     */
    public void shutdownAll() {
        log.info("Shutting down all agents...");
        agents.values().forEach(agent -> {
            try {
                agent.stop();
            } catch (Exception e) {
                log.error("Error stopping agent: {}", agent.getName(), e);
            }
        });
        agents.clear();
    }
}

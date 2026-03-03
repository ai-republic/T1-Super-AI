package com.airepublic.t1.config;

import com.airepublic.t1.agent.AgentManager;
import com.airepublic.t1.agent.AgentOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Ensures agents are loaded at application startup for REST API access.
 * This component initializes the master agent and loads all saved agent configurations
 * when the Spring application is ready.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentStartupLoader {

    private final AgentManager agentManager;
    private final AgentOrchestrator orchestrator;

    @EventListener(ApplicationReadyEvent.class)
    public void loadAgentsOnStartup() {
        log.info("Loading agents for REST API access...");

        try {
            // Initialize master agent if not already initialized
            agentManager.initializeMasterAgent(orchestrator);
            log.info("Master agent initialized for REST API");

            // Load all saved agent configurations from disk
            agentManager.loadSavedAgents(orchestrator);
            log.info("Saved agents loaded for REST API access");

        } catch (Exception e) {
            log.error("Error loading agents at startup", e);
        }
    }
}

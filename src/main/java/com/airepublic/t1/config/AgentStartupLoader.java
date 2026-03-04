package com.airepublic.t1.config;

import com.airepublic.t1.agent.AgentManager;
import com.airepublic.t1.agent.AgentOrchestrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;

/**
 * Ensures agents are loaded at application startup for REST API access.
 * This component loads all saved agent configurations when the Spring application is ready.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentStartupLoader {

    private final AgentManager agentManager;
    private final AgentOrchestrator orchestrator;
    private final AgentConfigService agentConfigService;

    @PostConstruct
    public void init() {
        log.info("🔧 AgentStartupLoader bean created and initialized");
    }

    @EventListener(ApplicationReadyEvent.class)
    public void loadAgentsOnStartup() {
        log.info("🚀 ApplicationReadyEvent received - Loading agents for REST API access...");

        try {
            // Load all saved agent configurations from disk
            agentManager.loadSavedAgents(orchestrator);
            log.info("Saved agents loaded for REST API access");

            // Switch to default agent from USER.md if available
            try {
                AgentConfigService.UserInfo userInfo = agentConfigService.readUserInfo();
                log.info("Read USER.md - default agent: '{}'", userInfo.defaultAgent);

                if (userInfo.defaultAgent != null && !userInfo.defaultAgent.isEmpty()) {
                    if (agentManager.hasAgent(userInfo.defaultAgent)) {
                        agentManager.switchToAgent(userInfo.defaultAgent);
                        log.info("✅ Switched to default agent: {}", userInfo.defaultAgent);
                    } else {
                        log.warn("⚠️ Default agent '{}' not found. Switching to first available agent.", userInfo.defaultAgent);
                        // Try to switch to the first available agent
                        java.util.List<com.airepublic.t1.agent.Agent> agents = agentManager.listAgents();
                        if (!agents.isEmpty()) {
                            String firstAgent = agents.get(0).getName();
                            agentManager.switchToAgent(firstAgent);
                            log.info("✅ Switched to first available agent: {}", firstAgent);
                        } else {
                            log.error("❌ No agents available! Please create an agent first.");
                        }
                    }
                } else {
                    log.warn("⚠️ No default agent specified in USER.md (empty or null)");
                    // No default agent in USER.md, use first available
                    java.util.List<com.airepublic.t1.agent.Agent> agents = agentManager.listAgents();
                    log.info("Found {} agents loaded in memory", agents.size());
                    if (!agents.isEmpty()) {
                        String firstAgent = agents.get(0).getName();
                        agentManager.switchToAgent(firstAgent);
                        log.info("✅ Switched to first available agent: {}", firstAgent);
                    } else {
                        log.error("❌ No agents available! Please create an agent first.");
                    }
                }
            } catch (Exception e) {
                log.error("❌ Error during agent switching at startup", e);
            }

        } catch (Exception e) {
            log.error("Error loading agents at startup", e);
        }
    }
}

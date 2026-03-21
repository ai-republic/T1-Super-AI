package com.airepublic.t1.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.airepublic.t1.session.SessionContextManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles application startup initialization
 * - Copies documentation files from resources to ~/.t1-super-ai/
 * - Initializes workspace with instructional files
 * - Checks if HATCH process is needed
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StartupInitializer {
    private final WorkspaceInitializer workspaceInitializer;
    private final ConfigurationLoader configurationLoader;
    private final SessionContextManager sessionContextManager;
    private final AgentConfigService agentConfigService;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("🚀 T1 Super AI starting up...");

        // Step 1: Initialize workspace (creates directories and instructional files in root)
        final boolean needsHatching = workspaceInitializer.initializeWorkspace();

        // Step 2: Synchronize team name across all services
        final String currentTeam = workspaceInitializer.getTeamName();
        agentConfigService.setTeamName(currentTeam);
        log.info("Synchronized team name '{}' across all services", currentTeam);

        // Step 3: Initialize configuration loader
        configurationLoader.setWorkspaceInitializer(workspaceInitializer);
        configurationLoader.initializeDirectories();

        // Step 4: Check if hatching is needed
        if (needsHatching) {
            log.info("🥚 HATCH process required - USER.md not found");
            log.info("📋 Instructions available in: ~/.t1-super-ai/HATCH.md");
            log.info("💡 Agent will guide you through setup after LLM configuration");
        } else {
            log.info("✅ Agent configured - USER.md loaded");

            // Show context summary
            final String contextSummary = sessionContextManager.getContextSummary();
            log.info("📋 Session Context:\n{}", contextSummary);
        }

        log.info("✅ T1 Super AI workspace initialized!");
    }
}

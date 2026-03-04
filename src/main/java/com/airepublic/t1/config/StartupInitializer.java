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

    // Use Paths.get() for cross-platform compatibility (Windows, Linux, macOS)
    private static final String WORKSPACE_DIR = Paths.get(System.getProperty("user.home"), ".t1-super-ai").toString();

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("🚀 T1 Super AI starting up...");

        // Step 1: Initialize workspace (creates directories and instructional files)
        final boolean needsHatching = workspaceInitializer.initializeWorkspace();

        // Step 2: Copy documentation files from user's home .t1-super-ai if they exist
        // Otherwise create them from embedded content
        copyDocumentationFiles();

        // Step 3: Initialize configuration loader
        configurationLoader.setWorkspaceInitializer(workspaceInitializer);
        configurationLoader.initializeDirectories();

        // Step 4: Check if hatching is needed
        if (needsHatching) {
            log.info("🥚 HATCH process required - CHARACTER.md not found");
            log.info("📋 Instructions available in: {}/HATCH.md", WORKSPACE_DIR);
            log.info("💡 Agent will guide you through setup after LLM configuration");
        } else {
            log.info("✅ Agent configured - CHARACTER.md loaded");

            // Show context summary
            final String contextSummary = sessionContextManager.getContextSummary();
            log.info("📋 Session Context:\n{}", contextSummary);
        }

        log.info("✅ T1 Super AI workspace initialized!");
    }

    /**
     * Copy documentation files from user's home .t1-super-ai to workspace
     * Files are expected to already exist there from previous runs
     */
    private void copyDocumentationFiles() {
        try {
            final Path userHome = Paths.get(System.getProperty("user.home"));
            final Path sourceBase = userHome.resolve(".t1-super-ai");
            final Path targetBase = Paths.get(WORKSPACE_DIR);

            // If source and target are the same, we're already in the right place
            if (sourceBase.toAbsolutePath().equals(targetBase.toAbsolutePath())) {
                log.debug("Workspace is already in correct location: {}", WORKSPACE_DIR);
                return;
            }

            // Files to potentially copy (if they don't exist in target)
            final String[] docFiles = {
                "HATCH.md",
                "USAGE.md",
                "README.md",
                "QUICK_REFERENCE.md",
                "CONFIGURATION_GUIDE.md",
                "CHARACTER.md.template"
            };

            int copiedCount = 0;
            for (final String filename : docFiles) {
                final Path targetPath = targetBase.resolve(filename);

                // Skip if file already exists
                if (Files.exists(targetPath)) {
                    continue;
                }

                // Try to copy from user's home .t1-super-ai
                final Path sourcePath = sourceBase.resolve(filename);
                if (Files.exists(sourcePath)) {
                    Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    copiedCount++;
                    log.debug("Copied {} to workspace", filename);
                }
            }

            if (copiedCount > 0) {
                log.info("📚 Copied {} documentation files to workspace", copiedCount);
            }

        } catch (final IOException e) {
            log.warn("Could not copy some documentation files: {}", e.getMessage());
        }
    }
}

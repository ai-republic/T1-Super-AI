package com.airepublic.t1;

import com.airepublic.t1.config.AgentConfigurationManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.Banner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Standalone CLI Application for T1 Super AI
 * This is a completely separate application from the web interface
 * It shares the same configs and services but runs independently
 *
 * Usage:
 *   java -cp target/t1-super-ai-1.0.0-SNAPSHOT.jar com.airepublic.t1.T1SuperAiCLI
 * Or use the provided launch script: run-cli.sh / run-cli.bat
 */
@Slf4j
@SpringBootApplication(exclude = {
        // Disable ALL OpenAI auto-configurations
        org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration.class,
        org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration.class,
        org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration.class,
        org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration.class,
        org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration.class,
        org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration.class,

        // Disable ALL Anthropic auto-configurations
        org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatAutoConfiguration.class,

        // Disable ALL Ollama auto-configurations
        org.springframework.ai.model.ollama.autoconfigure.OllamaChatAutoConfiguration.class,
        org.springframework.ai.model.ollama.autoconfigure.OllamaApiAutoConfiguration.class,
        org.springframework.ai.model.ollama.autoconfigure.OllamaEmbeddingAutoConfiguration.class
})
public class T1SuperAiCLI {

    // ANSI color codes for console output
    private static final String GREEN = "\033[32m";
    private static final String CYAN = "\033[36m";
    private static final String RESET = "\033[0m";

    public static void main(String[] args) {
        // Create standalone CLI application without web server
        SpringApplication app = new SpringApplication(T1SuperAiCLI.class);
        app.setBannerMode(Banner.Mode.OFF);

        // Disable web server for CLI mode and set CLI mode flag
        System.setProperty("spring.main.web-application-type", "none");
        System.setProperty("t1.mode", "cli");

        app.run(args);
    }

    /**
     * Helper method to output info messages to both log and console in green
     */
    private static void infoToConsole(String message) {
        log.info(message);
        System.out.println(GREEN + message + RESET);
    }

    /**
     * Helper method to output info messages in cyan
     */
    private static void cyanToConsole(String message) {
        log.info(message);
        System.out.println(CYAN + message + RESET);
    }

    @Bean
    public CommandLineRunner runCLI(
            org.springframework.core.env.Environment environment,
            AgentConfigurationManager configManager,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            com.airepublic.t1.cli.SplitPaneCLI splitPaneCLI,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            com.airepublic.t1.mcp.MCPServerRunner mcpServerRunner,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            com.airepublic.t1.mcp.MCPClient mcpClient,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            com.airepublic.t1.config.ToolFunctionConfiguration toolFunctionConfig,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            com.airepublic.t1.service.HatchingService hatchingService,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            com.airepublic.t1.config.WorkspaceInitializer workspaceInitializer,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            com.airepublic.t1.session.SessionContextManager sessionContextManager) {
        return args -> {
            // Only run if t1.mode is set to cli
            String mode = environment.getProperty("t1.mode", "");
            if (!"cli".equals(mode)) {
                log.debug("Skipping CLI runner - t1.mode is not 'cli' (current: {})", mode);
                return;
            }
            cyanToConsole("╔════════════════════════════════════════════════════════════╗");
            cyanToConsole("║          T1 Super AI - Command Line Interface            ║");
            cyanToConsole("╚════════════════════════════════════════════════════════════╝");
            infoToConsole("🚀 Starting CLI mode...");

            // STEP 1: Initialize workspace FIRST (before anything else)
            boolean needsHatching = false;
            if (workspaceInitializer != null) {
                needsHatching = workspaceInitializer.initializeWorkspace();
                if (needsHatching) {
                    infoToConsole("🥚 First run detected - workspace initialized");
                    infoToConsole("📚 Instructional files created in ~/.t1-super-ai/");
                } else {
                    infoToConsole("✅ Workspace already configured");
                    // Show context summary
                    if (sessionContextManager != null) {
                        String contextSummary = sessionContextManager.getContextSummary();
                        infoToConsole("📋 Session Context:\n" + contextSummary);
                    }
                }
            }

            // STEP 2: Start MCP server in background
            int mcpPort = -1;
            if (mcpServerRunner != null) {
                mcpPort = mcpServerRunner.startMCPServerInBackground();
            }

            // STEP 3: Check if configuration exists, otherwise run configuration wizard
            boolean wasJustConfigured = false;
            if (!configManager.isConfigured()) {
                infoToConsole("No configuration found. Starting configuration wizard...");
                configManager.runConfigurationWizard();
                wasJustConfigured = true;
            }

            // STEP 4: Connect to MCP server to access tools
            if (mcpClient != null && mcpPort > 0) {
                try {
                    infoToConsole("🔗 Connecting to local MCP server on port " + mcpPort + "...");
                    mcpClient.connectToSelf(mcpPort);
                    infoToConsole("✅ Connected to local MCP server - tools are now available");

                    // Register tools as Spring function beans for LLM access
                    if (toolFunctionConfig != null) {
                        toolFunctionConfig.registerToolFunctions();
                    }
                } catch (Exception e) {
                    log.error("❌ CRITICAL: Failed to connect to local MCP server. Tools will not be available!", e);
                    throw new RuntimeException("Cannot start without MCP server connection", e);
                }
            }

            // STEP 5: After configuration wizard, check if HATCH is needed
            if (wasJustConfigured) {
                infoToConsole("✅ LLM configured - agent ready for interaction");
                if (needsHatching && hatchingService != null) {
                    infoToConsole("🥚 HATCH process will start - agent will guide you through personality setup");
                    infoToConsole("📋 The agent will ask you questions to configure your preferences");
                }
            }

            // STEP 6: Start the CLI (this blocks)
            if (splitPaneCLI != null) {
                infoToConsole("✅ Starting Split-Pane CLI (Optimized for Windows, GitBash & Linux)");
                infoToConsole("💡 Features: Fixed input at bottom, scrolling output, responsive terminal, full navigation!");
                splitPaneCLI.start(needsHatching);
            } else {
                log.error("❌ SplitPaneCLI not available!");
                throw new RuntimeException("SplitPaneCLI is required but not found");
            }
        };
    }
}

package com.airepublic.t1;

import com.airepublic.t1.config.AgentConfigurationManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Main Web Application for T1 Super AI
 * This is the default application that starts the web server
 * Shares configs and services with T1SuperAiCLI but runs as separate application
 *
 * Usage:
 *   java -jar t1-super-ai.jar
 * Or use the provided launch script: run-webapp.sh / run-webapp.bat
 *
 * For CLI mode, use T1SuperAiCLI (run-cli.sh / run-cli.bat)
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
        ,

        // Disable VectorStore auto-configuration (manually configured in VectorStoreConfiguration)
        com.airepublic.vectorstore.spring.VectorStoreAutoConfiguration.class
})
public class T1SuperAiApplication {

    // ANSI color codes for console output
    private static final String GREEN = "\033[32m";
    private static final String CYAN = "\033[36m";
    private static final String RESET = "\033[0m";

    public static void main(String[] args) {
        // Set webapp mode flag
        System.setProperty("t1.mode", "webapp");

        SpringApplication app = new SpringApplication(T1SuperAiApplication.class);
        app.run(args);
    }

    /**
     * Helper method to output info messages to both log and console in green
     */
    private static void infoToConsole(String message) {
        log.info(message);
        System.out.println(GREEN + message + RESET);
    }

    @Bean
    public CommandLineRunner runWebApp(
            org.springframework.core.env.Environment environment,
            AgentConfigurationManager configManager,
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
            // Only run if t1.mode is set to webapp
            String mode = environment.getProperty("t1.mode", "");
            if (!"webapp".equals(mode)) {
                log.debug("Skipping webapp runner - t1.mode is not 'webapp' (current: {})", mode);
                return;
            }
            cyanToConsole("╔════════════════════════════════════════════════════════════╗");
            cyanToConsole("║          T1 Super AI - Web Application Mode              ║");
            cyanToConsole("╚════════════════════════════════════════════════════════════╝");
            infoToConsole("🚀 Starting web server...");

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
                        log.info("📋 Session Context:\n{}", contextSummary);
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
                log.warn("⚠️ No configuration found. Please configure via web UI or run CLI mode for wizard.");
                log.info("💡 Web UI will be available for configuration at http://localhost:8080");
            } else {
                infoToConsole("✅ Configuration loaded");
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

            // STEP 5: Web server is now running
            cyanToConsole("");
            cyanToConsole("┌────────────────────────────────────────────────────────┐");
            cyanToConsole("│  ✅ Web UI is now available at:                        │");
            cyanToConsole("│                                                        │");
            cyanToConsole("│      http://localhost:8080                             │");
            cyanToConsole("│                                                        │");
            cyanToConsole("│  💡 Open your browser to interact with agents          │");
            cyanToConsole("│  📝 For CLI mode, use: run-cli.sh or run-cli.bat      │");
            cyanToConsole("└────────────────────────────────────────────────────────┘");
            cyanToConsole("");

            if (needsHatching && hatchingService != null) {
                log.info("🥚 HATCH process required - configure via web UI");
            }
        };
    }

    /**
     * Helper method to output cyan messages
     */
    private static void cyanToConsole(String message) {
        System.out.println(CYAN + message + RESET);
    }
}

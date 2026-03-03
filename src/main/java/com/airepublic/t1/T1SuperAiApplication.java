package com.airepublic.t1;

// Removed unused CLI imports - only SplitPaneCLI is supported
import com.airepublic.t1.config.AgentConfigurationManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

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
public class T1SuperAiApplication {

    // ANSI color codes for console output
    private static final String GREEN = "\033[32m";
    private static final String RESET = "\033[0m";

    public static void main(String[] args) {
        SpringApplication.run(T1SuperAiApplication.class, args);
    }

    /**
     * Helper method to output info messages to both log and console in green
     */
    private static void infoToConsole(String message) {
        log.info(message);
        System.out.println(GREEN + message + RESET);
    }

    @Bean
    public CommandLineRunner run(
            AgentConfigurationManager configManager,
            @org.springframework.beans.factory.annotation.Autowired(required = false) com.airepublic.t1.cli.SplitPaneCLI splitPaneCLI,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            com.airepublic.t1.mcp.MCPServerRunner mcpServerRunner,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            com.airepublic.t1.mcp.MCPClient mcpClient,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            com.airepublic.t1.config.ToolFunctionConfiguration toolFunctionConfig,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            com.airepublic.t1.service.HatchingService hatchingService,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            com.airepublic.t1.agent.AgentSessionController sessionController,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            com.airepublic.t1.config.WorkspaceInitializer workspaceInitializer,
            @org.springframework.beans.factory.annotation.Autowired(required = false)
            com.airepublic.t1.session.SessionContextManager sessionContextManager) {
        return args -> {
            infoToConsole("🚀 T1 - Super AI starting up...");

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

            infoToConsole("Starting T1 Super AI CLI...");

            // STEP 3: Check if configuration exists, otherwise run configuration wizard
            boolean wasJustConfigured = false;
            if (!configManager.isConfigured()) {
                infoToConsole("No configuration found. Starting configuration wizard...");
                configManager.runConfigurationWizard();
                wasJustConfigured = true;
            }

            // MANDATORY: Connect to our own MCP server to access tools
            if (mcpClient != null && mcpPort > 0) {
                try {
                    infoToConsole("🔗 Connecting to local MCP server on port " + mcpPort + "...");
                    mcpClient.connectToSelf(mcpPort);
                    infoToConsole("✅ Connected to local MCP server - tools are now available");

                    // Now register tools as Spring function beans for LLM access
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
            // Using SplitPaneCLI - the only supported CLI for Windows, GitBash, and Linux
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

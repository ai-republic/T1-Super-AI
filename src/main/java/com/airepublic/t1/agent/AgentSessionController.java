package com.airepublic.t1.agent;

import com.airepublic.t1.service.HatchingService;
import com.airepublic.t1.session.SessionContextManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

/**
 * Controls agent session lifecycle including:
 * - Loading CHARACTER.md and USAGE.md as context
 * - Running HATCH process on first run
 * - Managing conversation with context
 */
@Slf4j
@Component
public class AgentSessionController {
    private final SessionContextManager sessionContextManager;
    private final HatchingService hatchingService;
    private ChatModel chatModel;

    private ChatClient chatClient;
    private boolean sessionInitialized = false;

    // ANSI color codes for CLI output
    private static final String GREEN = "\033[32m";
    private static final String RESET = "\033[0m";

    // Output callback for CLI integration
    private java.util.function.Consumer<String> outputCallback = null;

    public AgentSessionController(
            SessionContextManager sessionContextManager,
            HatchingService hatchingService) {
        this.sessionContextManager = sessionContextManager;
        this.hatchingService = hatchingService;
        // ChatModel will be set later when available
    }

    /**
     * Set the output callback for CLI integration
     */
    public void setOutputCallback(java.util.function.Consumer<String> callback) {
        this.outputCallback = callback;

        // Now that CLI is ready, check if we need to initialize or show status
        if (chatModel == null && !sessionInitialized) {
            infoToConsole("⏳ AgentSessionController waiting for ChatModel configuration");
        } else if (chatModel != null && !sessionInitialized) {
            initializeSession();
        }
    }

    public void setChatModel(ChatModel chatModel) {
        this.chatModel = chatModel;
        infoToConsole("ChatModel configured for AgentSessionController");
        // Initialize session when ChatModel becomes available
        if (!sessionInitialized) {
            initializeSession();
        }
    }

    @PostConstruct
    public void initialize() {
        // Don't output anything here - wait for CLI to be ready
        // The CLI will trigger initialization when it's ready
    }

    /**
     * Initialize a new session with CHARACTER.md and USAGE.md context
     */
    public void initializeSession() {
        if (chatModel == null) {
            log.warn("⚠️ Cannot initialize session - ChatModel not configured");
            return;
        }

        infoToConsole("🔄 Initializing agent session...");

        // Check if hatching is needed
        if (hatchingService.needsHatching()) {
            infoToConsole("🥚 HATCH process needed - starting initial setup");
            startHatchSession();
        } else {
            infoToConsole("✅ Loading configured agent session");
            startNormalSession();
        }

        sessionInitialized = true;
    }

    /**
     * Start a HATCH session for first-time setup
     */
    private void startHatchSession() {
        // Build chat client with HATCH context
        chatClient = ChatClient.builder(chatModel)
            .defaultSystem(hatchingService.getHatchInitiationMessage())
            .build();

        infoToConsole("🥚 HATCH session ready - user will be guided through setup");
    }

    /**
     * Start a normal session with CHARACTER.md and USAGE.md context
     */
    private void startNormalSession() {
        // Get context messages
        List<SessionContextManager.SessionMessage> contextMessages =
            sessionContextManager.buildContextMessages();

        // Build system context from CHARACTER.md and USAGE.md
        StringBuilder systemContext = new StringBuilder();
        systemContext.append("# Agent Configuration\n\n");

        for (SessionContextManager.SessionMessage msg : contextMessages) {
            if ("CHARACTER_PROFILE".equals(msg.getType())) {
                systemContext.append("## Your Character Profile\n\n");
                systemContext.append(msg.getContent());
                systemContext.append("\n\n");
            } else if ("USAGE_GUIDE".equals(msg.getType())) {
                systemContext.append("## Available Capabilities (Reference)\n\n");
                systemContext.append("You have access to a comprehensive usage guide. ");
                systemContext.append("Refer to this when users ask about capabilities or configuration.\n\n");
                // Don't include full USAGE.md in system prompt - too large
                // Instead, make it available as a reference
            }
        }

        systemContext.append("---\n\n");
        systemContext.append("Remember: CHARACTER.md defines your personality and how you should interact. ");
        systemContext.append("Use the configuration and preferences defined there in all conversations.");

        // Build chat client with context
        chatClient = ChatClient.builder(chatModel)
            .defaultSystem(systemContext.toString())
            .build();

        infoToConsole("✅ Session initialized with CHARACTER.md context");

        // Log context summary
        String summary = sessionContextManager.getContextSummary();
        infoToConsole("📋 Context loaded:\n" + summary);
    }

    /**
     * Send a message to the agent
     */
    public String sendMessage(String userMessage) {
        if (!sessionInitialized) {
            initializeSession();
        }

        try {
            String response = chatClient.prompt()
                .user(userMessage)
                .call()
                .content();

            return response;

        } catch (Exception e) {
            log.error("Error processing message", e);
            return "I encountered an error processing your message. Please try again.";
        }
    }

    /**
     * Check if this is a HATCH session
     */
    public boolean isHatchSession() {
        return hatchingService.needsHatching();
    }

    /**
     * Get the current chat client
     */
    public ChatClient getChatClient() {
        return chatClient;
    }

    /**
     * Helper method to output info messages to both log and CLI in green
     */
    private void infoToConsole(String message) {
        log.info(message);

        // If CLI callback is set, use it; otherwise fallback to System.out
        if (outputCallback != null) {
            outputCallback.accept(GREEN + message + RESET);
        } else {
            System.out.println(GREEN + message + RESET);
        }
    }
}

package com.airepublic.t1.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import com.airepublic.t1.agent.AgentManager;
import com.airepublic.t1.agent.AgentOrchestrator;
import com.airepublic.t1.config.AgentConfigService;
import com.airepublic.t1.config.WorkspaceInitializer;

import lombok.extern.slf4j.Slf4j;

/**
 * Service to handle the HATCH process - initial agent configuration via LLM interaction
 */
@Slf4j
@Service
public class HatchingService {
    private final WorkspaceInitializer workspaceInitializer;
    private final AgentConfigService agentConfigService;
    private final AgentManager agentManager;
    private final AgentOrchestrator orchestrator;
    private ChatModel chatModel;

    public HatchingService(final WorkspaceInitializer workspaceInitializer,
            final AgentConfigService agentConfigService,
            final AgentManager agentManager,
            final AgentOrchestrator orchestrator) {
        this.workspaceInitializer = workspaceInitializer;
        this.agentConfigService = agentConfigService;
        this.agentManager = agentManager;
        this.orchestrator = orchestrator;
        // ChatModel will be set later when available
    }

    public void setChatModel(final ChatModel chatModel) {
        this.chatModel = chatModel;
        log.info("ChatModel configured for HatchingService");
    }

    /**
     * Run the HATCH process interactively
     * This is called on first run when CHARACTER.md doesn't exist
     */
    public void runHatchProcess() {
        try {
            log.info("🥚 Starting HATCH process - configuring your AI agent");

            if (chatModel == null) {
                log.warn("⚠️ ChatModel not available yet - HATCH process will run when LLM is configured");
                return;
            }

            // Read HATCH.md content
            final String hatchContent = workspaceInitializer.readHatchMd();
            if (hatchContent == null) {
                log.error("HATCH.md not found - cannot run setup");
                return;
            }

            // Create system prompt for hatching
            final String systemPrompt = buildHatchSystemPrompt();

            // Use LLM to conduct the interview
            log.info("🤖 Initiating conversation with user to gather configuration...");

            final ChatClient chatClient = ChatClient.builder(chatModel).build();

            final String hatchPrompt = String.format(
                    "I need to configure this T1 Super AI for the user. " +
                            "Please read the HATCH.md guide below and help me gather the necessary information " +
                            "from the user to create their CHARACTER.md profile.\n\n" +
                            "HATCH Guide:\n%s\n\n" +
                            "Please start by greeting the user and explaining that we're setting up their AI agent. " +
                            "Then ask the questions from Step 1 (About You). " +
                            "After collecting all information, I'll create their CHARACTER.md file.\n\n" +
                            "Important: Be friendly, conversational, and guide them through each step. " +
                            "Don't ask all questions at once - have a natural conversation.",
                            hatchContent
                    );

            // This would be an interactive process in a real implementation
            // For now, we'll create a minimal CHARACTER.md
            log.info("📝 HATCH process requires interactive session");
            log.info("💡 Tip: User should interact with the agent to complete setup");

        } catch (final Exception e) {
            log.error("Error during HATCH process", e);
        }
    }

    /**
     * Process HATCH responses and create USER.md and initial agent
     * This is called when the user has provided all answers
     */
    public void completeHatchProcess(final Map<String, String> responses) {
        try {
            log.info("✅ Creating USER.md and initial agent from collected information");

            // User information (for USER.md)
            final String userName = responses.getOrDefault("user_name", "User");
            final String userPronouns = responses.getOrDefault("user_pronouns", "they/them");
            final String userWorkFocus = responses.getOrDefault("user_work_focus", "Software Development");

            // Agent information (for CHARACTER.md)
            final String agentName = responses.getOrDefault("agent_name", "Assistant");
            final String agentRole = responses.getOrDefault("agent_role", "AI Assistant");
            final String agentPurpose = responses.getOrDefault("agent_purpose", "Help with coding and development tasks");
            final String specialization = responses.getOrDefault("agent_specialization", "General software development");
            final String style = responses.getOrDefault("communication_style", "Professional and friendly");
            final String personality = responses.getOrDefault("agent_personality", "Helpful, patient, thorough");
            final String emojiPreference = responses.getOrDefault("emoji_preference", "sparingly");

            // Create USER.md in workspace root with initial agent as default
            workspaceInitializer.createUserProfile(
                    userName,
                    userPronouns,
                    userWorkFocus,
                    agentName
                    );

            // Create initial agent in agents folder
            agentConfigService.createAgentFolder(agentName);
            final Path agentFolder = agentConfigService.getAgentFolder(agentName);

            // Extract provider and model from responses or use defaults
            final String providerStr = responses.getOrDefault("provider", "openai");
            final String model = responses.getOrDefault("model", "gpt-4");
            final com.airepublic.t1.model.AgentConfiguration.LLMProvider provider =
                    com.airepublic.t1.model.AgentConfiguration.LLMProvider.valueOf(providerStr.toUpperCase());

            // Create full agent configuration
            final com.airepublic.t1.model.IndividualAgentConfig config = new com.airepublic.t1.model.IndividualAgentConfig(
                    agentName,                                          // name
                    agentRole,                                          // role
                    agentPurpose,                                       // purpose
                    specialization,                                     // specialization
                    style,                                              // style
                    personality,                                        // personality
                    emojiPreference,                                    // emojiPreference
                    "active",                                           // status
                    java.time.LocalDateTime.now(),                      // createdAt
                    java.time.LocalDateTime.now(),                      // lastModifiedAt
                    provider,                                           // provider
                    model                                               // model
            );

            // Create CHARACTER.md for the agent
            agentConfigService.createCharacterMd(config, com.airepublic.t1.config.AgentConfigService.getCharacterBehaviorTemplate());
            log.info("📄 Created CHARACTER.md for agent '{}'", agentName);

            // Create USAGE.md for the agent
            agentConfigService.createUsageMd(agentName, config);
            log.info("📋 Created USAGE.md for agent '{}'", agentName);

            // Load the newly created agent into AgentManager and switch to it
            try {
                // Create and register the agent in AgentManager
                agentManager.createAgent(agentName, orchestrator, config);

                // Switch to the newly created agent
                agentManager.switchToAgent(agentName);

                log.info("✅ Agent '{}' loaded and activated in AgentManager", agentName);
            } catch (final IllegalArgumentException e) {
                // Agent might already exist from a previous partial hatch
                if (agentManager.hasAgent(agentName)) {
                    agentManager.switchToAgent(agentName);
                    log.info("✅ Switched to existing agent '{}'", agentName);
                } else {
                    log.error("Failed to load agent '{}' into AgentManager", agentName, e);
                }
            }

            log.info("🎉 HATCH process complete! USER.md and initial agent '{}' created", agentName);
            log.info("✅ Your agent '{}' is now configured and ready in agents/{}", agentName, agentName);

        } catch (final IOException e) {
            log.error("Error completing HATCH process", e);
        }
    }

    /**
     * Extract configuration from LLM conversation
     * This parses the LLM's collected information into structured data
     */
    public Map<String, String> extractHatchData(final String conversationSummary) {
        final Map<String, String> data = new HashMap<>();

        if (chatModel == null) {
            log.warn("ChatModel not available - cannot extract HATCH data");
            return data;
        }

        // Use LLM to extract structured data from conversation
        final ChatClient chatClient = ChatClient.builder(chatModel).build();

        final String extractionPrompt = String.format(
                "Based on the following conversation where we collected user information for agent setup, " +
                        "please extract the following fields in a structured format:\n\n" +
                        "Conversation:\n%s\n\n" +
                        "Please provide the extracted information in this exact format:\n" +
                        "user_name: [value]\n" +
                        "user_pronouns: [value]\n" +
                        "user_work_focus: [value]\n" +
                        "agent_name: [value]\n" +
                        "agent_role: [value]\n" +
                        "agent_purpose: [value]\n" +
                        "agent_specialization: [value]\n" +
                        "agent_personality: [value]\n" +
                        "communication_style: [value]\n" +
                        "constraints: [value]\n" +
                        "emoji_preference: [value]\n",
                        conversationSummary
                );

        try {
            final String response = chatClient.prompt()
                    .user(extractionPrompt)
                    .call()
                    .content();

            // Parse the response
            final String[] lines = response.split("\n");
            for (final String line : lines) {
                if (line.contains(":")) {
                    final String[] parts = line.split(":", 2);
                    if (parts.length == 2) {
                        final String key = parts[0].trim();
                        final String value = parts[1].trim();
                        data.put(key, value);
                    }
                }
            }

            log.info("✅ Extracted {} configuration values", data.size());

        } catch (final Exception e) {
            log.error("Error extracting HATCH data", e);
        }

        return data;
    }

    /**
     * Build system prompt for HATCH process
     */
    private String buildHatchSystemPrompt() {
        return "You are helping set up an T1 Super AI for a new user. " +
                "Your role is to guide them through the initial configuration process " +
                "by asking questions from the HATCH.md guide. " +
                "Be friendly, patient, and conversational. " +
                "After gathering all information, you'll help create their CHARACTER.md profile. " +
                "The CHARACTER.md will be loaded as context in every future session, " +
                "so make sure to capture the user's preferences accurately.";
    }

    /**
     * Check if hatching is needed
     */
    public boolean needsHatching() {
        return workspaceInitializer.needsHatching();
    }

    /**
     * Get HATCH initiation message for user
     */
    public String getHatchInitiationMessage() {
        return "🥚 **Welcome to T1 Super AI!**\n\n" +
                "This appears to be your first time running the agent. " +
                "Let's set up your personalized AI assistant!\n\n" +
                "I'll ask you a few questions to understand:\n" +
                "- Who you are and what you work on\n" +
                "- What you'd like to call me (your agent)\n" +
                "- How I should communicate with you\n" +
                "- What tools and capabilities you need\n\n" +
                "This will only take a few minutes, and you can always adjust these settings later.\n\n" +
                "**Ready to begin?** Let's start with some basics about you! 🚀";
    }
}

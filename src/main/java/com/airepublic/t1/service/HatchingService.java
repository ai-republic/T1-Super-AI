package com.airepublic.t1.service;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import com.airepublic.t1.agent.AgentManager;
import com.airepublic.t1.agent.AgentOrchestrator;
import com.airepublic.t1.config.AgentConfigService;
import com.airepublic.t1.config.AgentConfigurationManager;
import com.airepublic.t1.config.WorkspaceInitializer;
import com.airepublic.t1.model.AgentConfiguration;
import com.airepublic.t1.model.IndividualAgentConfig;

import lombok.extern.slf4j.Slf4j;

/**
 * Service to handle the HATCH process - initial agent configuration via LLM interaction
 */
@Slf4j
@Service
public class HatchingService {
    private final WorkspaceInitializer workspaceInitializer;
    private final AgentConfigService agentConfigService;
    private final AgentConfigurationManager configManager;
    private final AgentManager agentManager;
    private final AgentOrchestrator orchestrator;
    private ChatModel chatModel;

    public HatchingService(final WorkspaceInitializer workspaceInitializer,
            final AgentConfigService agentConfigService,
            final AgentConfigurationManager configManager,
            final AgentManager agentManager,
            final AgentOrchestrator orchestrator) {
        this.workspaceInitializer = workspaceInitializer;
        this.agentConfigService = agentConfigService;
        this.configManager = configManager;
        this.agentManager = agentManager;
        this.orchestrator = orchestrator;
        // ChatModel will be set later when available
    }

    public void setChatModel(final ChatModel chatModel) {
        this.chatModel = chatModel;
        log.info("ChatModel configured for HatchingService");
    }

    /**
     * Process HATCH responses and create USER.md and initial agent
     * This is called when the user has provided all answers via the CLI wizard
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

            // Extract provider and model from responses or use defaults
            final AgentConfiguration agentConfiguration = configManager.getConfiguration();
            final AgentConfiguration.LLMProvider provider = agentConfiguration.getDefaultProvider();
            final String model = agentConfiguration.getLlmConfigs().get(provider).getModel();

            // Create full agent configuration
            final IndividualAgentConfig config = new IndividualAgentConfig(
                    agentName,                                          // name
                    agentRole,                                          // role
                    agentPurpose,                                       // purpose
                    specialization,                                     // specialization
                    style,                                              // style
                    personality,                                        // personality
                    emojiPreference,                                    // emojiPreference
                    "active",                                           // status
                    LocalDateTime.now(), // createdAt
                    LocalDateTime.now(), // lastModifiedAt
                    provider,                                           // provider
                    model                                               // model
                    );

            // Create CHARACTER.md for the agent
            agentConfigService.createCharacterMd(config, AgentConfigService.getCharacterBehaviorTemplate());
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

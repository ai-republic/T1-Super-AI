package com.airepublic.t1.service;

import com.airepublic.t1.config.WorkspaceInitializer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Service to handle the HATCH process - initial agent configuration via LLM interaction
 */
@Slf4j
@Service
public class HatchingService {
    private final WorkspaceInitializer workspaceInitializer;
    private ChatModel chatModel;

    public HatchingService(WorkspaceInitializer workspaceInitializer) {
        this.workspaceInitializer = workspaceInitializer;
        // ChatModel will be set later when available
    }

    public void setChatModel(ChatModel chatModel) {
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
            String hatchContent = workspaceInitializer.readHatchMd();
            if (hatchContent == null) {
                log.error("HATCH.md not found - cannot run setup");
                return;
            }

            // Create system prompt for hatching
            String systemPrompt = buildHatchSystemPrompt();

            // Use LLM to conduct the interview
            log.info("🤖 Initiating conversation with user to gather configuration...");

            ChatClient chatClient = ChatClient.builder(chatModel).build();

            String hatchPrompt = String.format(
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

        } catch (Exception e) {
            log.error("Error during HATCH process", e);
        }
    }

    /**
     * Process HATCH responses and create CHARACTER.md
     * This is called when the user has provided all answers
     */
    public void completeHatchProcess(Map<String, String> responses) {
        try {
            log.info("✅ Creating CHARACTER.md from collected information");

            String userName = responses.getOrDefault("user_name", "User");
            String userPronouns = responses.getOrDefault("user_pronouns", "they/them");
            String userFocus = responses.getOrDefault("user_focus", "Software Development");
            String agentName = responses.getOrDefault("agent_name", "Assistant");
            String agentPurpose = responses.getOrDefault("agent_purpose", "Help with coding and development tasks");
            String communicationStyle = responses.getOrDefault("communication_style", "Professional and friendly");
            String personalityTraits = responses.getOrDefault("personality_traits", "Helpful, patient, thorough");
            String emojiPreference = responses.getOrDefault("emoji_preference", "Sparingly");

            workspaceInitializer.createCharacterMd(
                userName,
                userPronouns,
                userFocus,
                agentName,
                agentPurpose,
                communicationStyle,
                personalityTraits,
                emojiPreference
            );

            log.info("🎉 HATCH process complete! CHARACTER.md created");
            log.info("✅ Your agent '{}' is now configured and ready", agentName);

        } catch (IOException e) {
            log.error("Error completing HATCH process", e);
        }
    }

    /**
     * Extract configuration from LLM conversation
     * This parses the LLM's collected information into structured data
     */
    public Map<String, String> extractHatchData(String conversationSummary) {
        Map<String, String> data = new HashMap<>();

        if (chatModel == null) {
            log.warn("ChatModel not available - cannot extract HATCH data");
            return data;
        }

        // Use LLM to extract structured data from conversation
        ChatClient chatClient = ChatClient.builder(chatModel).build();

        String extractionPrompt = String.format(
            "Based on the following conversation where we collected user information for agent setup, " +
            "please extract the following fields in a structured format:\n\n" +
            "Conversation:\n%s\n\n" +
            "Please provide the extracted information in this exact format:\n" +
            "user_name: [value]\n" +
            "user_pronouns: [value]\n" +
            "user_focus: [value]\n" +
            "agent_name: [value]\n" +
            "agent_purpose: [value]\n" +
            "communication_style: [value]\n" +
            "personality_traits: [value]\n" +
            "emoji_preference: [value]\n",
            conversationSummary
        );

        try {
            String response = chatClient.prompt()
                .user(extractionPrompt)
                .call()
                .content();

            // Parse the response
            String[] lines = response.split("\n");
            for (String line : lines) {
                if (line.contains(":")) {
                    String[] parts = line.split(":", 2);
                    if (parts.length == 2) {
                        String key = parts[0].trim();
                        String value = parts[1].trim();
                        data.put(key, value);
                    }
                }
            }

            log.info("✅ Extracted {} configuration values", data.size());

        } catch (Exception e) {
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

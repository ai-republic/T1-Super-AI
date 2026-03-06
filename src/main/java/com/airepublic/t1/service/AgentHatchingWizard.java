package com.airepublic.t1.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import org.springframework.stereotype.Service;

import com.airepublic.t1.config.AgentConfigService;
import com.airepublic.t1.model.AgentConfiguration.LLMProvider;
import com.airepublic.t1.model.IndividualAgentConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Interactive wizard for creating new agents through a hatching process.
 * Guides users through defining agent character, purpose, and configuration.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentHatchingWizard {
    private final AgentConfigService agentConfigService;

    /**
     * Run the complete hatching wizard for a new agent
     */
    public HatchResult runHatchingProcess(final String agentName, final LLMProvider defaultProvider, final String defaultModel) {
        try {
            System.out.println("\n🥚 ═══════════════════════════════════════════════════");
            System.out.println("   AGENT HATCHING PROCESS");
            System.out.println("   Creating: " + agentName);
            System.out.println("═════════════════════════════════════════════════════\n");

            System.out.println("This wizard will guide you through creating a personalized agent.");
            System.out.println("Answer the following questions to define your agent's character.\n");

            // Create folder
            agentConfigService.createAgentFolder(agentName);

            // Collect hatching data
            final Map<String, String> hatchData = collectHatchingData();

            // Create agent configuration
            final IndividualAgentConfig config = createAgentConfig(
                    agentName,
                    hatchData,
                    defaultProvider,
                    defaultModel
                    );


            // Create CHARACTER.md
            agentConfigService.createCharacterMd(config, AgentConfigService.getCharacterGuidlinesTemplate());

            // Create USAGE.md
            agentConfigService.createUsageMd(agentName, config);

            System.out.println("\n🎉 ═══════════════════════════════════════════════════");
            System.out.println("   AGENT SUCCESSFULLY HATCHED!");
            System.out.println("═════════════════════════════════════════════════════");
            System.out.println("\n✅ Agent '" + agentName + "' is ready to use!");
            System.out.println("📁 Location: " + agentConfigService.getAgentFolder(agentName));
            System.out.println("\n📄 Files created:");
            System.out.println("   • config.json    - Agent configuration");
            System.out.println("   • CHARACTER.md   - Agent personality and role");
            System.out.println("   • USAGE.md       - Usage guide and examples");
            System.out.println("\n💡 To activate: /agent use " + agentName);
            System.out.println();

            return new HatchResult(true, config, hatchData);

        } catch (final Exception e) {
            log.error("Error during hatching process", e);
            System.err.println("\n❌ Error during hatching: " + e.getMessage());
            return new HatchResult(false, null, null);
        }
    }

    /**
     * Collect hatching data through interactive prompts
     */
    private Map<String, String> collectHatchingData() {
        final Map<String, String> data = new HashMap<>();
        final Scanner scanner = new Scanner(System.in);

        try {
            // Agent Role
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("STEP 1: Define Agent Role");
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("What role will this agent play?");
            System.out.println("Examples: Code Reviewer, Data Analyst, DevOps Engineer");
            System.out.print("\n→ Agent Role: ");
            System.out.flush();
            data.put("agent_role", readLine(scanner, "AI Assistant"));

            // Agent Purpose
            System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("STEP 2: Define Purpose");
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("What is the main purpose of this agent?");
            System.out.println("Examples: Review pull requests for code quality, Analyze data patterns");
            System.out.print("\n→ Purpose: ");
            System.out.flush();
            data.put("agent_purpose", readLine(scanner, "General purpose assistance"));

            // Agent Purpose
            System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("STEP 3: Define Specialization");
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("What are this agent's areas of expertise?");
            System.out.println("Examples: Java Spring Boot, Python data science, AWS infrastructure");
            System.out.print("\n→ Specialties (or Enter to skip): ");
            System.out.flush();
            data.put("agent_specialization", readLine(scanner, "General purpose assistance"));

            // Agent Personality
            System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("STEP 4: Define Personality");
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("Describe the agent's personality traits.");
            System.out.println("Examples: Thorough and detail-oriented, Encouraging and supportive");
            System.out.print("\n→ Personality: ");
            System.out.flush();
            data.put("agent_personality", readLine(scanner, "Professional and helpful"));

            // Communication Style
            System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("STEP 5: Communication Style");
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("How should the agent communicate?");
            System.out.println("Examples: Technical and precise, Friendly and casual, Formal and detailed");
            System.out.print("\n→ Style: ");
            System.out.flush();
            data.put("communication_style", readLine(scanner, "Clear and concise"));

            // Constraints
            System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("STEP 6: Constraints (Optional)");
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("Any constraints or boundaries for this agent?");
            System.out.println("Examples: Only provide code reviews, Focus on backend only");
            System.out.print("\n→ Constraints (or Enter to skip): ");
            System.out.flush();
            data.put("constraints", readLine(scanner, "None"));

        } finally {
            // Don't close the scanner as it would close System.in
            // Just leave it for garbage collection
        }

        return data;
    }

    /**
     * Create agent configuration from hatching data
     */
    private IndividualAgentConfig createAgentConfig(
            final String agentName,
            final Map<String, String> hatchData,
            final LLMProvider provider,
            final String model) {

        final String role = hatchData.get("agent_role");
        final String purpose = hatchData.get("agent_purpose");
        final String specialization = hatchData.getOrDefault("agent_specialization", "General software development");
        final String personality = hatchData.get("agent_personality");
        final String style = hatchData.get("communication_style");
        final String emojiPreference = hatchData.getOrDefault("emoji_preference", "sparingly");

        final IndividualAgentConfig config = new IndividualAgentConfig(agentName, role, purpose, specialization, style, personality, emojiPreference, AgentConfigService.getCharacterGuidlinesTemplate(), "active", LocalDateTime.now(),
                LocalDateTime.now(), provider, model);

        return config;
    }

    /**
     * Read a line with default value
     */
    private String readLine(final Scanner scanner, final String defaultValue) {
        if (scanner.hasNextLine()) {
            final String input = scanner.nextLine();
            if (input == null || input.trim().isEmpty()) {
                return defaultValue;
            }
            return input.trim();
        }
        return defaultValue;
    }

    /**
     * Result of the hatching process
     */
    public static class HatchResult {
        private final boolean success;
        private final IndividualAgentConfig config;
        private final Map<String, String> hatchData;

        public HatchResult(final boolean success, final IndividualAgentConfig config, final Map<String, String> hatchData) {
            this.success = success;
            this.config = config;
            this.hatchData = hatchData;
        }

        public boolean isSuccess() {
            return success;
        }

        public IndividualAgentConfig getConfig() {
            return config;
        }

        public Map<String, String> getHatchData() {
            return hatchData;
        }
    }
}

package com.airepublic.t1.service;

import com.airepublic.t1.config.AgentConfigService;
import com.airepublic.t1.model.AgentConfiguration.LLMProvider;
import com.airepublic.t1.model.IndividualAgentConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

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
    public HatchResult runHatchingProcess(String agentName, LLMProvider defaultProvider, String defaultModel) {
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
            Map<String, String> hatchData = collectHatchingData();

            // Create agent configuration
            IndividualAgentConfig config = createAgentConfig(
                agentName,
                hatchData,
                defaultProvider,
                defaultModel
            );

            // Save configuration
            agentConfigService.saveAgentConfig(config);

            // Create CHARACTER.md
            agentConfigService.createCharacterMd(agentName, hatchData);

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

        } catch (Exception e) {
            log.error("Error during hatching process", e);
            System.err.println("\n❌ Error during hatching: " + e.getMessage());
            return new HatchResult(false, null, null);
        }
    }

    /**
     * Collect hatching data through interactive prompts
     */
    private Map<String, String> collectHatchingData() {
        Map<String, String> data = new HashMap<>();
        Scanner scanner = new Scanner(System.in);

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

            // Agent Personality
            System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("STEP 3: Define Personality");
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("Describe the agent's personality traits.");
            System.out.println("Examples: Thorough and detail-oriented, Encouraging and supportive");
            System.out.print("\n→ Personality: ");
            System.out.flush();
            data.put("agent_personality", readLine(scanner, "Professional and helpful"));

            // Communication Style
            System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("STEP 4: Communication Style");
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("How should the agent communicate?");
            System.out.println("Examples: Technical and precise, Friendly and casual, Formal and detailed");
            System.out.print("\n→ Style: ");
            System.out.flush();
            data.put("communication_style", readLine(scanner, "Clear and concise"));

            // Specialties
            System.out.println("\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("STEP 5: Specialties (Optional)");
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("What are this agent's areas of expertise?");
            System.out.println("Examples: Java Spring Boot, Python data science, AWS infrastructure");
            System.out.print("\n→ Specialties (or Enter to skip): ");
            System.out.flush();
            data.put("specialties", readLine(scanner, "General software development"));

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
            String agentName,
            Map<String, String> hatchData,
            LLMProvider provider,
            String model) {

        String role = hatchData.get("agent_role");
        String purpose = hatchData.get("agent_purpose");

        return new IndividualAgentConfig(
            agentName,
            role,
            purpose,
            provider,
            model
        );
    }

    /**
     * Read a line with default value
     */
    private String readLine(Scanner scanner, String defaultValue) {
        if (scanner.hasNextLine()) {
            String input = scanner.nextLine();
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

        public HatchResult(boolean success, IndividualAgentConfig config, Map<String, String> hatchData) {
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

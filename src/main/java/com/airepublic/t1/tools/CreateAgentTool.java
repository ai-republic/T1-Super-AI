package com.airepublic.t1.tools;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.airepublic.t1.agent.AgentManager;
import com.airepublic.t1.agent.AgentOrchestrator;
import com.airepublic.t1.config.AgentConfigService;
import com.airepublic.t1.config.AgentConfigurationManager;
import com.airepublic.t1.model.AgentConfiguration.LLMProvider;
import com.airepublic.t1.model.IndividualAgentConfig;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Tool to create a new agent with complete CHARACTER.md profile.
 * Allows creating fully configured agents with personality, role, and configuration.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreateAgentTool implements AgentTool {

    private final AgentManager agentManager;
    private final AgentConfigService agentConfigService;
    private final AgentOrchestrator orchestrator;
    private final AgentConfigurationManager configManager;

    @Override
    public String getName() {
        return "create_agent";
    }

    @Override
    public String getDescription() {
        return """
                Create a new agent with a complete character profile and configuration.

                This tool creates a new agent with all necessary files:
                - CHARACTER.md: Agent's personality, role, and purpose
                - config.json: Agent's LLM configuration
                - USAGE.md: Guide for using the agent

                Parameters:
                - name (required): Unique identifier for the agent
                - role: Agent's role or title (default: "AI Assistant")
                - purpose: Agent's main purpose (default: "General purpose assistance")
                - specialization: Areas of expertise (default: "General AI assistance")
                - communication_style: How the agent communicates (default: "Clear and concise")
                - personality: Personality traits (default: "Professional and helpful")
                - emoji_preference: Emoji usage - "freely", "sparingly", "none" (default: "sparingly")
                - guidelines: Guidelines or Limitations (default: "None specified")
                - context: System prompt for the agent (default: uses role)
                - provider: LLM provider - OPENAI, ANTHROPIC, or OLLAMA (optional)
                - model: Model name (optional, uses provider default if not specified)

                Example:
                {
                  "name": "code-reviewer",
                  "role": "Senior Code Reviewer",
                  "purpose": "Review code for quality, security, and best practices",
                  "specialization": "Java, Spring Boot, security, testing",
                  "communication_style": "Technical with specific examples",
                  "personality": "Thorough, constructive, and detail-oriented",
                  "emoji_preference": "sparingly",
                  "guidelines": "Focus on security and maintainability",
                  "provider": "OPENAI",
                  "model": "gpt-4o"
                }

                Returns: Success message with agent details and file locations.
                """;
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        final Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");

        final Map<String, Object> properties = new HashMap<>();

        // Required: name
        final Map<String, Object> nameProp = new HashMap<>();
        nameProp.put("type", "string");
        nameProp.put("description", "Unique name for the agent (required)");
        properties.put("name", nameProp);

        // Optional: role
        final Map<String, Object> roleProp = new HashMap<>();
        roleProp.put("type", "string");
        roleProp.put("description", "Agent's role or title (e.g., 'Senior Java Developer', 'Data Analyst')");
        properties.put("role", roleProp);

        // Optional: purpose
        final Map<String, Object> purposeProp = new HashMap<>();
        purposeProp.put("type", "string");
        purposeProp.put("description", "Main purpose of the agent (what it's designed to do)");
        properties.put("purpose", purposeProp);

        // Optional: personality
        final Map<String, Object> personalityProp = new HashMap<>();
        personalityProp.put("type", "string");
        personalityProp.put("description", "Personality traits (e.g., 'Professional and patient', 'Encouraging and supportive')");
        properties.put("personality", personalityProp);

        // Optional: communication_style
        final Map<String, Object> commStyleProp = new HashMap<>();
        commStyleProp.put("type", "string");
        commStyleProp.put("description", "How the agent communicates (e.g., 'Clear with code examples', 'Formal and detailed')");
        properties.put("communication_style", commStyleProp);

        // Optional: specialties
        final Map<String, Object> specialtiesProp = new HashMap<>();
        specialtiesProp.put("type", "string");
        specialtiesProp.put("description", "Areas of expertise (e.g., 'Java, Spring Boot, REST APIs')");
        properties.put("specialties", specialtiesProp);

        // Optional: guidelines
        final Map<String, Object> guidelinesProp = new HashMap<>();
        guidelinesProp.put("type", "string");
        guidelinesProp.put("description", "Guidelines or Limitations (e.g., 'Requires unit tests for all code')");
        properties.put("guidelines", guidelinesProp);

        // Optional: context
        final Map<String, Object> contextProp = new HashMap<>();
        contextProp.put("type", "string");
        contextProp.put("description", "System prompt/context for the agent (uses role if not specified)");
        properties.put("context", contextProp);

        // Optional: provider
        final Map<String, Object> providerProp = new HashMap<>();
        providerProp.put("type", "string");
        providerProp.put("description", "LLM provider: OPENAI, ANTHROPIC, or OLLAMA");
        providerProp.put("enum", new String[]{"OPENAI", "ANTHROPIC", "OLLAMA"});
        properties.put("provider", providerProp);

        // Optional: model
        final Map<String, Object> modelProp = new HashMap<>();
        modelProp.put("type", "string");
        modelProp.put("description", "Model name (e.g., 'gpt-4o', 'claude-3-5-sonnet-20241022', 'llama3')");
        properties.put("model", modelProp);

        schema.put("properties", properties);
        schema.put("required", new String[]{"name"});

        return schema;
    }

    @Override
    public String execute(final Map<String, Object> arguments) throws Exception {
        // Validate and extract required parameter
        String name = (String) arguments.get("name");
        if (name == null || name.trim().isEmpty()) {
            return "Error: 'name' is required and cannot be empty";
        }
        name = name.trim();

        // Check if agent already exists
        if (agentManager.hasAgent(name)) {
            return "Error: Agent '" + name + "' already exists. Choose a different name or delete the existing agent first.";
        }

        if (agentConfigService.agentConfigExists(name)) {
            return "Error: Agent configuration for '" + name + "' already exists on disk. Use a different name or delete the configuration first.";
        }

        try {
            // Extract optional parameters with defaults
            final String role = getStringOrDefault(arguments, "role", "AI Assistant");
            final String purpose = getStringOrDefault(arguments, "purpose", "General purpose assistance");
            final String specialization = getStringOrDefault(arguments, "specialization", "General AI assistance");
            final String communicationStyle = getStringOrDefault(arguments, "communication_style", "Clear and concise");
            final String personality = getStringOrDefault(arguments, "personality", "Professional and helpful");
            final String emojiPreference = getStringOrDefault(arguments, "emoji_preference", "sparingly");
            final String guidelines = getStringOrDefault(arguments, "guidelines", AgentConfigService.getCharacterGuidlinesTemplate());

            // Extract provider and model (optional - use defaults from global config if not specified)
            LLMProvider provider = null;
            final String providerStr = (String) arguments.get("provider");
            if (providerStr != null && !providerStr.trim().isEmpty()) {
                try {
                    provider = LLMProvider.valueOf(providerStr.toUpperCase());
                } catch (final IllegalArgumentException e) {
                    return "Error: Invalid provider '" + providerStr + "'. Valid options: OPENAI, ANTHROPIC, OLLAMA";
                }
            } else {
                // Use default provider from global config
                provider = configManager.getConfiguration().getDefaultProvider();
            }

            String model = (String) arguments.get("model");
            if (model == null || model.trim().isEmpty()) {
                // Use default model from global config
                final var llmConfig = configManager.getConfiguration().getLlmConfigs().get(provider);
                model = llmConfig != null ? llmConfig.getModel() : "default";
            }

            // Step 1: Create agent folder
            log.info("Creating agent folder for '{}'", name);
            agentConfigService.createAgentFolder(name);

            // Step 2: Create agent configuration
            log.info("Creating configuration for '{}'", name);
            final IndividualAgentConfig config = new IndividualAgentConfig(
                    name,                       // name
                    role,                       // role
                    purpose,                    // purpose
                    specialization,             // specialization
                    communicationStyle,         // style
                    personality,                // personality
                    emojiPreference,            // emojiPreference
                    AgentConfigService.getCharacterGuidlinesTemplate(), // guidelines
                    "active",                   // status
                    LocalDateTime.now(),        // createdAt
                    LocalDateTime.now(),        // lastModifiedAt
                    provider,                   // provider
                    model                       // model
                    );

            // Step 3: Create CHARACTER.md
            log.info("Creating CHARACTER.md for '{}'", name);
            agentConfigService.createCharacterMd(config, AgentConfigService.getCharacterGuidlinesTemplate());

            // Step 4: Create USAGE.md
            log.info("Creating USAGE.md for '{}'", name);
            agentConfigService.createUsageMd(name, config);

            // Step 5: Register agent in AgentManager
            log.info("Registering agent '{}' in AgentManager", name);
            agentManager.createAgent(name, orchestrator, config);

            // Build success message
            final StringBuilder result = new StringBuilder();
            result.append("✅ Agent '").append(name).append("' successfully created!\n\n");
            result.append("📋 Agent Profile:\n");
            result.append("   • Name: ").append(name).append("\n");
            result.append("   • Role: ").append(role).append("\n");
            result.append("   • Purpose: ").append(purpose).append("\n");
            result.append("   • Personality: ").append(personality).append("\n");
            result.append("   • Communication Style: ").append(communicationStyle).append("\n");
            result.append("   • Specialization: ").append(specialization).append("\n");
            result.append("   • Emoji Preference: ").append(emojiPreference).append("\n");
            result.append("   • Guidelines: ").append(guidelines).append("\n\n");

            result.append("⚙️  Configuration:\n");
            if (provider != null) {
                result.append("   • Provider: ").append(provider).append("\n");
                result.append("   • Model: ").append(model != null ? model : "(using provider default)").append("\n");
            } else {
                result.append("   • Provider: (using system default)\n");
                result.append("   • Model: (using system default)\n");
            }
            result.append("\n");

            result.append("📁 Files Created:\n");
            result.append("   • ").append(agentConfigService.getCharacterMdPath(name)).append("\n");
            result.append("   • ").append(agentConfigService.getAgentFolder(name).resolve("config.json")).append("\n");
            result.append("   • ").append(agentConfigService.getUsageMdPath(name)).append("\n\n");

            result.append("💡 Next Steps:\n");
            result.append("   • To activate: /agent use ").append(name).append("\n");
            result.append("   • To send a message: use send_message_to_agent tool\n");
            result.append("   • To list all agents: use list_agents tool\n");

            log.info("Successfully created agent '{}'", name);
            return result.toString();

        } catch (final Exception e) {
            log.error("Error creating agent '{}'", name, e);

            // Attempt cleanup on error
            try {
                agentConfigService.deleteAgentConfig(name);
                if (agentManager.hasAgent(name)) {
                    agentManager.removeAgent(name);
                }
            } catch (final Exception cleanupError) {
                log.error("Error during cleanup", cleanupError);
            }

            return "Error: Failed to create agent '" + name + "': " + e.getMessage();
        }
    }

    /**
     * Helper method to get string value or default
     */
    private String getStringOrDefault(final Map<String, Object> arguments, final String key, final String defaultValue) {
        final Object value = arguments.get(key);
        if (value == null) {
            return defaultValue;
        }
        final String strValue = value.toString().trim();
        return strValue.isEmpty() ? defaultValue : strValue;
    }
}

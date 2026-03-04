package com.airepublic.t1.tools;

import com.airepublic.t1.agent.Agent;
import com.airepublic.t1.agent.AgentManager;
import com.airepublic.t1.config.AgentConfigService;
import com.airepublic.t1.model.IndividualAgentConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Tool to update an agent's CHARACTER.md profile.
 * Allows updating role, purpose, personality, communication style, specialties, and constraints.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UpdateAgentCharacterTool implements AgentTool {

    private final AgentManager agentManager;
    private final AgentConfigService agentConfigService;

    @Override
    public String getName() {
        return "update_agent_character";
    }

    @Override
    public String getDescription() {
        return """
                Update an existing agent's CHARACTER.md profile.

                This tool allows you to update specific fields in an agent's character profile:
                - role: Agent's role or title
                - purpose: Agent's main purpose
                - personality: Personality traits
                - communication_style: How the agent communicates
                - specialties: Areas of expertise
                - constraints: Limitations or guidelines

                You only need to provide the fields you want to update - other fields remain unchanged.

                Parameters:
                - name (required): Name of the agent to update
                - role (optional): New role or title
                - purpose (optional): New purpose description
                - personality (optional): New personality traits
                - communication_style (optional): New communication style
                - specialties (optional): New specialties
                - constraints (optional): New constraints

                Example - Update only role and specialties:
                {
                  "name": "code-reviewer",
                  "role": "Principal Code Reviewer",
                  "specialties": "Java, Spring Boot, Kotlin, security, performance optimization"
                }

                Example - Update all fields:
                {
                  "name": "data-analyst",
                  "role": "Senior Data Scientist",
                  "purpose": "Advanced data analysis and machine learning",
                  "personality": "Analytical, innovative, and detail-oriented",
                  "communication_style": "Data-driven with visualizations and statistical insights",
                  "specialties": "Python, R, machine learning, deep learning, data visualization",
                  "constraints": "Always validate data quality and model assumptions"
                }

                Returns: Success message with updated fields and new CHARACTER.md content.
                """;
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new HashMap<>();

        // Required: name
        Map<String, Object> nameProp = new HashMap<>();
        nameProp.put("type", "string");
        nameProp.put("description", "Name of the agent to update (required)");
        properties.put("name", nameProp);

        // Optional: role
        Map<String, Object> roleProp = new HashMap<>();
        roleProp.put("type", "string");
        roleProp.put("description", "New role or title for the agent");
        properties.put("role", roleProp);

        // Optional: purpose
        Map<String, Object> purposeProp = new HashMap<>();
        purposeProp.put("type", "string");
        purposeProp.put("description", "New purpose description for the agent");
        properties.put("purpose", purposeProp);

        // Optional: personality
        Map<String, Object> personalityProp = new HashMap<>();
        personalityProp.put("type", "string");
        personalityProp.put("description", "New personality traits for the agent");
        properties.put("personality", personalityProp);

        // Optional: communication_style
        Map<String, Object> commStyleProp = new HashMap<>();
        commStyleProp.put("type", "string");
        commStyleProp.put("description", "New communication style for the agent");
        properties.put("communication_style", commStyleProp);

        // Optional: specialties
        Map<String, Object> specialtiesProp = new HashMap<>();
        specialtiesProp.put("type", "string");
        specialtiesProp.put("description", "New specialties or areas of expertise");
        properties.put("specialties", specialtiesProp);

        // Optional: constraints
        Map<String, Object> constraintsProp = new HashMap<>();
        constraintsProp.put("type", "string");
        constraintsProp.put("description", "New constraints or guidelines");
        properties.put("constraints", constraintsProp);

        schema.put("properties", properties);
        schema.put("required", new String[]{"name"});

        return schema;
    }

    @Override
    public String execute(Map<String, Object> arguments) throws Exception {
        // Validate and extract required parameter
        String name = (String) arguments.get("name");
        if (name == null || name.trim().isEmpty()) {
            return "Error: 'name' is required and cannot be empty";
        }
        name = name.trim();

        // Check if agent exists
        if (!agentConfigService.agentConfigExists(name)) {
            return "Error: Agent '" + name + "' does not exist. Use create_agent to create a new agent.";
        }

        try {
            // Build updates map with only provided fields
            Map<String, String> updates = new HashMap<>();
            int updateCount = 0;

            if (arguments.containsKey("role") && arguments.get("role") != null) {
                String role = arguments.get("role").toString().trim();
                if (!role.isEmpty()) {
                    updates.put("agent_role", role);
                    updateCount++;
                }
            }

            if (arguments.containsKey("purpose") && arguments.get("purpose") != null) {
                String purpose = arguments.get("purpose").toString().trim();
                if (!purpose.isEmpty()) {
                    updates.put("agent_purpose", purpose);
                    updateCount++;
                }
            }

            if (arguments.containsKey("personality") && arguments.get("personality") != null) {
                String personality = arguments.get("personality").toString().trim();
                if (!personality.isEmpty()) {
                    updates.put("agent_personality", personality);
                    updateCount++;
                }
            }

            if (arguments.containsKey("communication_style") && arguments.get("communication_style") != null) {
                String commStyle = arguments.get("communication_style").toString().trim();
                if (!commStyle.isEmpty()) {
                    updates.put("communication_style", commStyle);
                    updateCount++;
                }
            }

            if (arguments.containsKey("specialties") && arguments.get("specialties") != null) {
                String specialties = arguments.get("specialties").toString().trim();
                if (!specialties.isEmpty()) {
                    updates.put("specialties", specialties);
                    updateCount++;
                }
            }

            if (arguments.containsKey("constraints") && arguments.get("constraints") != null) {
                String constraints = arguments.get("constraints").toString().trim();
                if (!constraints.isEmpty()) {
                    updates.put("constraints", constraints);
                    updateCount++;
                }
            }

            if (updateCount == 0) {
                return "Error: No fields provided to update. Please provide at least one field to update (role, purpose, personality, communication_style, specialties, or constraints).";
            }

            // Load existing config from CHARACTER.md
            log.info("Loading existing config for agent '{}'", name);
            IndividualAgentConfig config = agentConfigService.loadIndividualAgentConfig(name);

            // Apply updates to the config
            if (updates.containsKey("role")) {
                config.setRole(updates.get("role"));
            }
            if (updates.containsKey("purpose")) {
                config.setPurpose(updates.get("purpose"));
            }
            if (updates.containsKey("personality")) {
                config.setPersonality(updates.get("personality"));
            }
            if (updates.containsKey("communication_style")) {
                config.setStyle(updates.get("communication_style"));
            }
            if (updates.containsKey("specialization")) {
                config.setSpecialization(updates.get("specialization"));
            }

            // Update timestamp
            config.updateLastModified();

            // Update CHARACTER.md
            log.info("Updating CHARACTER.md for agent '{}' with {} field(s)", name, updateCount);
            agentConfigService.updateCharacterMd(config);

            // If the agent is loaded in AgentManager, trigger a session context reload
            if (agentManager.hasAgent(name)) {
                Agent agent = agentManager.getAgent(name);
                if (agent != null && agent.getOrchestrator() != null) {
                    // If this is the current agent, reload its context
                    if (name.equals(agentManager.getCurrentAgentName())) {
                        agent.getOrchestrator().reloadSessionContext(name);
                        log.info("Reloaded session context for agent '{}' after CHARACTER.md update", name);
                    }
                }
            }

            // Read the updated CHARACTER.md content
            String updatedContent = agentConfigService.readCharacterMd(name);

            // Build success message
            StringBuilder result = new StringBuilder();
            result.append("✅ Agent '").append(name).append("' CHARACTER.md updated successfully!\n\n");
            result.append("📝 Updated Fields (").append(updateCount).append("):\n");

            if (updates.containsKey("agent_role")) {
                result.append("   • Role: ").append(updates.get("agent_role")).append("\n");
            }
            if (updates.containsKey("agent_purpose")) {
                result.append("   • Purpose: ").append(updates.get("agent_purpose")).append("\n");
            }
            if (updates.containsKey("agent_personality")) {
                result.append("   • Personality: ").append(updates.get("agent_personality")).append("\n");
            }
            if (updates.containsKey("communication_style")) {
                result.append("   • Communication Style: ").append(updates.get("communication_style")).append("\n");
            }
            if (updates.containsKey("specialties")) {
                result.append("   • Specialties: ").append(updates.get("specialties")).append("\n");
            }
            if (updates.containsKey("constraints")) {
                result.append("   • Constraints: ").append(updates.get("constraints")).append("\n");
            }

            result.append("\n📄 Updated CHARACTER.md:\n");
            result.append("─".repeat(80)).append("\n");
            result.append(updatedContent);
            result.append("\n").append("─".repeat(80)).append("\n\n");

            result.append("💡 Next Steps:\n");
            result.append("   • The CHARACTER.md file has been updated\n");
            if (agentManager.hasAgent(name) && name.equals(agentManager.getCurrentAgentName())) {
                result.append("   • Session context has been reloaded with new character profile\n");
            } else {
                result.append("   • Switch to this agent to use the updated profile: /agent use ").append(name).append("\n");
            }
            result.append("   • Changes take effect immediately for new conversations\n");

            log.info("Successfully updated CHARACTER.md for agent '{}'", name);
            return result.toString();

        } catch (Exception e) {
            log.error("Error updating CHARACTER.md for agent '{}'", name, e);
            return "Error: Failed to update agent '" + name + "': " + e.getMessage();
        }
    }
}

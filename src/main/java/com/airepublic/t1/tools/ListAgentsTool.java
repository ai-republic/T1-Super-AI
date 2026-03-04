package com.airepublic.t1.tools;

import com.airepublic.t1.agent.Agent;
import com.airepublic.t1.agent.AgentManager;
import com.airepublic.t1.model.IndividualAgentConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tool to list all available agents with their roles and purposes.
 * Helps agents discover and select the right agent to communicate with.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ListAgentsTool implements AgentTool {

    private final AgentManager agentManager;

    @Override
    public String getName() {
        return "list_agents";
    }

    @Override
    public String getDescription() {
        return """
                List all available agents in the system with their names, roles, and purposes.

                Use this tool to:
                - Discover what agents are available
                - Find the right agent for a specific task
                - Learn about each agent's capabilities and expertise

                Returns information about each agent including:
                - Name: The agent's unique identifier
                - Role: The agent's role or title
                - Purpose: What the agent is designed to do
                - Status: Current operational status
                - Is Current: Whether this is you (the current agent)

                Use the send_message_to_agent tool to communicate with any of these agents.
                """;
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");
        schema.put("properties", new HashMap<>());
        // No parameters needed
        return schema;
    }

    @Override
    public String execute(Map<String, Object> arguments) throws Exception {
        try {
            List<Agent> agents = agentManager.listAgents();
            String currentAgentName = agentManager.getCurrentAgentName();

            if (agents.isEmpty()) {
                return "No agents available in the system.";
            }

            StringBuilder result = new StringBuilder();
            result.append("Available Agents:\n");
            result.append("=".repeat(80)).append("\n\n");

            for (Agent agent : agents) {
                IndividualAgentConfig config = agent.getConfig();
                String role = config != null ? config.getRole() : "General Purpose Agent";
                String purpose = config != null ? config.getPurpose() : null;
                String specialization = config != null ? config.getSpecialization() : null;

                boolean isCurrent = agent.getName().equals(currentAgentName);

                result.append(String.format("Name: %s%s\n",
                        agent.getName(),
                        isCurrent ? " (YOU)" : ""));
                result.append(String.format("Role: %s\n", role));

                if (purpose != null && !purpose.isEmpty()) {
                    result.append(String.format("Purpose: %s\n", purpose));
                }

                if (specialization != null && !specialization.isEmpty()) {
                    result.append(String.format("Specialization: %s\n", specialization));
                }

                result.append(String.format("Status: %s\n", agent.getStatus()));
                result.append(String.format("Conversations: %d\n", agent.getConversationHistory().size()));

                if (config != null && config.getProvider() != null) {
                    result.append(String.format("Provider: %s/%s\n",
                            config.getProvider(),
                            config.getModel() != null ? config.getModel() : "default"));
                }

                result.append("\n");
            }

            result.append("=".repeat(80)).append("\n");
            result.append(String.format("Total: %d agent(s)\n", agents.size()));
            result.append("\nTo communicate with an agent, use: send_message_to_agent\n");

            return result.toString();

        } catch (Exception e) {
            log.error("Error listing agents", e);
            return "Error: Failed to list agents: " + e.getMessage();
        }
    }
}

package com.airepublic.t1.tools;

import com.airepublic.t1.agent.Agent;
import com.airepublic.t1.agent.AgentManager;
import com.airepublic.t1.agent.AgentOrchestrator;
import com.airepublic.t1.model.IndividualAgentConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Tool that enables inter-agent communication.
 * Allows one agent to send messages to another agent and receive responses.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SendMessageToAgentTool implements AgentTool {

    private final AgentManager agentManager;
    private final AgentOrchestrator orchestrator;

    @Override
    public String getName() {
        return "send_message_to_agent";
    }

    @Override
    public String getDescription() {
        return """
                Send a message to another agent and get their response.

                Use this tool to collaborate with other specialized agents in the system.
                Each agent has unique capabilities and expertise areas.

                Before sending a message:
                1. Use 'list_agents' tool to discover available agents
                2. Review the agent's role and purpose to ensure it matches your need
                3. Send a clear, specific message explaining what you need

                The tool will:
                - Temporarily switch to the target agent
                - Send your message
                - Wait for the agent's response
                - Switch back to you
                - Return the response

                Example use cases:
                - Ask a code-specialist agent to review code
                - Request data analysis from an analyst agent
                - Get writing help from a creative writer agent
                - Consult a domain expert for specific knowledge
                """;
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new HashMap<>();

        // agent_name parameter
        Map<String, Object> agentNameProp = new HashMap<>();
        agentNameProp.put("type", "string");
        agentNameProp.put("description", "Name of the agent to send the message to. Use list_agents tool to discover available agents.");
        properties.put("agent_name", agentNameProp);

        // message parameter
        Map<String, Object> messageProp = new HashMap<>();
        messageProp.put("type", "string");
        messageProp.put("description", "The message to send to the agent. Be clear and specific about what you need.");
        properties.put("message", messageProp);

        schema.put("properties", properties);
        schema.put("required", new String[]{"agent_name", "message"});

        return schema;
    }

    @Override
    public String execute(Map<String, Object> arguments) throws Exception {
        String agentName = (String) arguments.get("agent_name");
        String message = (String) arguments.get("message");

        // Validate inputs
        if (agentName == null || agentName.trim().isEmpty()) {
            return "Error: agent_name is required. Use list_agents tool to see available agents.";
        }

        if (message == null || message.trim().isEmpty()) {
            return "Error: message is required. Please provide a message to send to the agent.";
        }

        // Check if agent exists
        if (!agentManager.hasAgent(agentName)) {
            return String.format(
                "Error: Agent '%s' not found. Use list_agents tool to see available agents.",
                agentName
            );
        }

        try {
            // Get the target agent
            Agent targetAgent = agentManager.getAgent(agentName);

            // Save current agent
            String currentAgentName = agentManager.getCurrentAgentName();
            boolean needToSwitch = !currentAgentName.equals(agentName);

            log.info("Agent '{}' sending message to agent '{}'", currentAgentName, agentName);

            // Switch to target agent if needed
            if (needToSwitch) {
                agentManager.switchToAgent(agentName);
            }

            // Process the message through the target agent's orchestrator
            long startTime = System.currentTimeMillis();
            String response = orchestrator.processMessage(message);
            long endTime = System.currentTimeMillis();

            // Switch back if needed
            if (needToSwitch) {
                agentManager.switchToAgent(currentAgentName);
            }

            // Get agent details for context
            IndividualAgentConfig config = targetAgent.getConfig();
            String agentRole = config != null ? config.getRole() : "Agent";

            log.info("Agent '{}' received response from agent '{}' in {}ms",
                    currentAgentName, agentName, (endTime - startTime));

            // Return formatted response
            return String.format(
                """
                Response from %s (%s):

                %s

                [Response time: %dms]
                """,
                agentName,
                agentRole != null ? agentRole : "Agent",
                response,
                (endTime - startTime)
            );

        } catch (Exception e) {
            log.error("Error sending message to agent: {}", agentName, e);
            return String.format(
                "Error: Failed to send message to agent '%s': %s",
                agentName,
                e.getMessage()
            );
        }
    }
}

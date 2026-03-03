package com.airepublic.t1.cli;

/**
 * Common interface for all CLI implementations
 */
public interface CLI {
    /**
     * Update the prompt to show a different agent name
     */
    void updatePromptAgent(String agentName);

    /**
     * Stop the CLI
     */
    void stop();

    /**
     * Start interactive agent creation wizard
     * @param agentName The name of the agent to create
     */
    void startAgentCreationWizard(String agentName);
}

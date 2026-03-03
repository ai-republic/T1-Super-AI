package com.airepublic.t1.cli;

import com.airepublic.t1.agent.Agent;
import com.airepublic.t1.agent.AgentManager;
import com.airepublic.t1.agent.AgentOrchestrator;
import com.airepublic.t1.agent.ToolRegistry;
import com.airepublic.t1.config.AgentConfigService;
import com.airepublic.t1.config.AgentConfigurationManager;
import com.airepublic.t1.mcp.MCPClient;
import com.airepublic.t1.model.AgentConfiguration;
import com.airepublic.t1.model.AgentConfiguration.LLMProvider;
import com.airepublic.t1.model.AgentConfiguration.TaskType;
import com.airepublic.t1.model.AgentConfiguration.TaskModelConfig;
import com.airepublic.t1.model.IndividualAgentConfig;
import com.airepublic.t1.plugins.PluginManager;
import com.airepublic.t1.plugins.Skill;
import com.airepublic.t1.service.AgentHatchingWizard;
import com.airepublic.t1.skills.SkillManager;
import com.airepublic.t1.tools.AutoModelSelectorTool;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class CommandHandler {
    private final AgentConfigurationManager configManager;
    private final AgentConfigService agentConfigService;
    private final PluginManager pluginManager;
    private final SkillManager skillManager;
    private final ToolRegistry toolRegistry;
    private final MCPClient mcpClient;
    private final CLIFormatter formatter;
    private CLI cli; // Will be set by the CLI implementation
    private final AgentOrchestrator orchestrator;
    private final AgentManager agentManager;
    private final AutoModelSelectorTool autoModelSelector;
    private final AgentHatchingWizard hatchingWizard;

    // Constructor
    public CommandHandler(
            AgentConfigurationManager configManager,
            AgentConfigService agentConfigService,
            PluginManager pluginManager,
            SkillManager skillManager,
            ToolRegistry toolRegistry,
            MCPClient mcpClient,
            CLIFormatter formatter,
            AgentOrchestrator orchestrator,
            AgentManager agentManager,
            AutoModelSelectorTool autoModelSelector,
            AgentHatchingWizard hatchingWizard) {
        this.configManager = configManager;
        this.agentConfigService = agentConfigService;
        this.pluginManager = pluginManager;
        this.skillManager = skillManager;
        this.toolRegistry = toolRegistry;
        this.mcpClient = mcpClient;
        this.formatter = formatter;
        this.orchestrator = orchestrator;
        this.agentManager = agentManager;
        this.autoModelSelector = autoModelSelector;
        this.hatchingWizard = hatchingWizard;
    }

    /**
     * Set the CLI implementation (called by CLI on startup)
     */
    public void setCLI(CLI cli) {
        this.cli = cli;
    }

    public void handleCommand(String input) {
        String[] parts = input.substring(1).split("\\s+", 2);
        String command = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1] : "";

        switch (command) {
            case "help" -> showHelp();
            case "config" -> handleConfig();
            case "provider" -> handleProvider(args);
            case "model" -> handleModel(args);
            case "auto-model" -> handleAutoModel(args);
            case "classify" -> handleClassify(args);
            case "mcp" -> handleMCP(args);
            case "plugin" -> handlePlugin(args);
            case "skill" -> handleSkill(args);
            case "agent" -> handleAgent(args);
            case "reload" -> handleReload();
            case "clear" -> clearScreen();
            case "exit", "quit" -> handleExit();
            default -> formatter.printError("Unknown command: " + command + ". Type /help for available commands.");
        }
    }

    private void showHelp() {
        String help = """

                Available Commands:

                /help                    Show this help message
                /config                  Run configuration wizard
                /provider [name]         Switch LLM provider (openai, anthropic, ollama)
                /model [name]            Change or view current model (e.g., gpt-4o, claude-3-opus)
                /auto-model [on|off]     Enable/disable automatic model selection based on prompt
                /classify <prompt>       Test LLM classification on a prompt (debugging)
                /mcp list                List connected MCP servers
                /mcp tools [name]        List tools from MCP server (or 'local' for plugin tools)
                /plugin list             List loaded plugins
                /plugin tools            List all tools from plugins
                /plugin reload           Reload all plugins
                /skill list              List available skills
                /skill create            Create a new skill
                /agent create <name>     Create a new agent (forks current session)
                /agent list              List all agents
                /agent use <name>        Switch to a different agent
                /agent remove <name>     Remove an agent
                /reload                  Reload CHARACTER.md and USAGE.md context
                /clear                   Clear the screen
                /exit, /quit             Exit the application

                For normal conversation, just type your message without a slash.
                """;

        formatter.printInfo(help);
    }

    private void handleConfig() {
        formatter.printInfo("Starting configuration wizard...");
        configManager.runConfigurationWizard();
        formatter.printSuccess("Configuration updated!");
    }

    private void handleProvider(String args) {
        if (args.isEmpty()) {
            formatter.printInfo("Current provider: " +
                    configManager.getConfiguration().getDefaultProvider());
            return;
        }

        try {
            LLMProvider provider = LLMProvider.valueOf(args.toUpperCase());
            configManager.updateProvider(provider);
            formatter.printSuccess("Switched to provider: " + provider);
        } catch (IllegalArgumentException e) {
            formatter.printError("Invalid provider. Available: openai, anthropic, ollama");
        }
    }

    private void handleModel(String args) {
        if (args.isEmpty()) {
            showModelSelectionMenu();
            return;
        }

        // Check if args is a number (menu selection)
        try {
            int selection = Integer.parseInt(args);
            handleModelSelection(selection);
            return;
        } catch (NumberFormatException e) {
            // Not a number, treat as model name
        }

        // Change model directly by name
        try {
            configManager.updateModel(args);
            LLMProvider currentProvider = configManager.getConfiguration().getDefaultProvider();
            formatter.printSuccess("Model changed to: " + args + " (" + currentProvider + ")");
            formatter.printInfo("💡 Note: The change takes effect for new conversations.");
        } catch (Exception e) {
            formatter.printError("Error changing model: " + e.getMessage());
        }
    }

    private void handleModelSelection(int selection) {
        AgentConfiguration config = configManager.getConfiguration();
        LLMProvider currentProvider = config.getDefaultProvider();

        // Rebuild the model options map (same as in showModelSelectionMenu)
        Map<Integer, ModelOption> modelOptions = new LinkedHashMap<>();
        int optionNumber = 1;

        // 1. Default provider model
        var defaultLlmConfig = config.getLlmConfigs().get(currentProvider);
        if (defaultLlmConfig != null) {
            String defaultModel = defaultLlmConfig.getModel();
            modelOptions.put(optionNumber++, new ModelOption(defaultModel, currentProvider, "Default model"));
        }

        // 2. Task-specific models - show ALL
        if (!config.getTaskModels().isEmpty()) {
            for (TaskType taskType : TaskType.values()) {
                TaskModelConfig taskConfig = config.getTaskModels().get(taskType);
                if (taskConfig != null) {
                    modelOptions.put(optionNumber++,
                        new ModelOption(taskConfig.getModel(), taskConfig.getProvider(),
                                      "For " + taskType.getDisplayName()));
                }
            }
        }

        // Custom option
        int customOptionNumber = optionNumber;
        modelOptions.put(customOptionNumber, new ModelOption(null, currentProvider, "Custom"));

        // Validate selection
        if (selection < 1 || selection > customOptionNumber) {
            formatter.printError("Invalid choice. Please select 1-" + customOptionNumber);
            formatter.printInfo("Tip: Run /model to see the menu again");
            return;
        }

        ModelOption selected = modelOptions.get(selection);

        if (selected.model == null) {
            // Custom model - need to prompt
            formatter.printInfo("To use a custom model, type: /model <model-name>");
            formatter.printInfo("Example: /model gpt-4-turbo-preview");
            return;
        }

        // Apply the selected model
        try {
            configManager.updateModel(selected.model);
            formatter.printSuccess("Model changed to: " + selected.model + " (" + selected.provider + ")");
            formatter.printInfo("💡 Note: The change takes effect for new conversations.");
        } catch (Exception e) {
            formatter.printError("Error changing model: " + e.getMessage());
        }
    }

    private void showModelSelectionMenu() {
        AgentConfiguration config = configManager.getConfiguration();
        String currentModel = configManager.getCurrentModel();
        LLMProvider currentProvider = config.getDefaultProvider();

        // Show header
        formatter.printInfo("\n╔═══════════════════════════════════════════════════════════════╗");
        formatter.printInfo("║  🎯 Model Selection Menu                                      ║");
        formatter.printInfo("╚═══════════════════════════════════════════════════════════════╝");
        formatter.printInfo("\n📌 Current: " + currentModel + " (" + currentProvider + ")");

        // Collect all available models
        Map<Integer, ModelOption> modelOptions = new LinkedHashMap<>();
        int optionNumber = 1;

        // 1. Default provider model
        formatter.printInfo("\n🔧 Default Provider Model:");
        var defaultLlmConfig = config.getLlmConfigs().get(currentProvider);
        if (defaultLlmConfig != null) {
            String defaultModel = defaultLlmConfig.getModel();
            modelOptions.put(optionNumber, new ModelOption(defaultModel, currentProvider, "Default model"));
            formatter.printInfo("  " + optionNumber++ + ". " + defaultModel + " (" + currentProvider + ") - Default");
        }

        // 2. Task-specific models - show ALL, no filtering
        if (!config.getTaskModels().isEmpty()) {
            formatter.printInfo("\n🎯 Task-Specific Models:");
            for (TaskType taskType : TaskType.values()) {
                TaskModelConfig taskConfig = config.getTaskModels().get(taskType);
                if (taskConfig != null) {
                    modelOptions.put(optionNumber,
                        new ModelOption(taskConfig.getModel(), taskConfig.getProvider(),
                                      "For " + taskType.getDisplayName()));
                    formatter.printInfo("  " + optionNumber++ + ". " + taskConfig.getModel() +
                        " (" + taskConfig.getProvider() + ") - " + taskType.getDisplayName());
                }
            }
        }

        // Show custom option
        formatter.printInfo("\n  " + optionNumber + ". ✏️  Enter custom model name");
        modelOptions.put(optionNumber, new ModelOption(null, currentProvider, "Custom"));

        // Show instructions
        formatter.printInfo("\n💡 Type the number and press Enter, or just press Enter to cancel");
        formatter.printInfo("Example: /model 2");
        formatter.printInfo("");
    }

    // Helper class to store model options
    private static class ModelOption {
        String model;
        LLMProvider provider;
        String description;

        ModelOption(String model, LLMProvider provider, String description) {
            this.model = model;
            this.provider = provider;
            this.description = description;
        }
    }

    private void handleAutoModel(String args) {
        args = args.trim().toLowerCase();

        // If no arguments, show current status
        if (args.isEmpty()) {
            boolean isEnabled = orchestrator.isAutoModelSelectionEnabled();
            formatter.printInfo("\n╔═══════════════════════════════════════════════════════════════╗");
            formatter.printInfo("║  🎯 Automatic Model Selection                                ║");
            formatter.printInfo("╚═══════════════════════════════════════════════════════════════╝");
            formatter.printInfo("\n📊 Status: " + (isEnabled ? "✅ ENABLED" : "❌ DISABLED"));
            formatter.printInfo("\nAutomatic model selection analyzes your prompts to detect the task type");
            formatter.printInfo("and automatically selects the appropriate task-specific model.");
            formatter.printInfo("\n💡 Usage:");
            formatter.printInfo("  /auto-model on      Enable automatic model selection");
            formatter.printInfo("  /auto-model off     Disable automatic model selection");
            formatter.printInfo("  /auto-model         Show current status\n");

            // Show configured task models
            AgentConfiguration config = configManager.getConfiguration();
            if (!config.getTaskModels().isEmpty()) {
                formatter.printInfo("🎯 Configured Task-Specific Models:");
                for (TaskType taskType : TaskType.values()) {
                    TaskModelConfig taskConfig = config.getTaskModels().get(taskType);
                    if (taskConfig != null) {
                        formatter.printInfo("  • " + taskType.getDisplayName() + ": " +
                            taskConfig.getProvider() + " - " + taskConfig.getModel());
                    }
                }
            } else {
                formatter.printInfo("⚠️  No task-specific models configured.");
                formatter.printInfo("   Run /config to configure task-specific models.");
            }
            formatter.printInfo("");
            return;
        }

        // Toggle on/off
        switch (args) {
            case "on", "enable", "enabled", "true", "1" -> {
                orchestrator.setAutoModelSelectionEnabled(true);
                formatter.printSuccess("✅ Automatic model selection ENABLED");
                formatter.printInfo("💡 Your prompts will now be analyzed to automatically select");
                formatter.printInfo("   the most appropriate task-specific model.");
            }
            case "off", "disable", "disabled", "false", "0" -> {
                orchestrator.setAutoModelSelectionEnabled(false);
                formatter.printSuccess("❌ Automatic model selection DISABLED");
                formatter.printInfo("💡 The default provider model will be used for all prompts.");
            }
            default -> {
                formatter.printError("Invalid argument: " + args);
                formatter.printInfo("Usage: /auto-model [on|off]");
            }
        }
    }

    private void handleClassify(String args) {
        if (args.trim().isEmpty()) {
            formatter.printError("Usage: /classify <prompt to classify>");
            formatter.printInfo("Example: /classify Write a Python function to sort an array");
            return;
        }

        formatter.printInfo("\n╔═══════════════════════════════════════════════════════════════╗");
        formatter.printInfo("║  🔍 Prompt Classification Test                               ║");
        formatter.printInfo("╚═══════════════════════════════════════════════════════════════╝");
        formatter.printInfo("\n📝 Analyzing prompt: \"" + args + "\"");
        formatter.printInfo("\n🔄 Sending to LLM for classification...\n");

        try {
            // Get detailed classification with explanation
            String analysis = autoModelSelector.getDetailedAnalysis(args);

            formatter.printInfo("🎯 Classification Result:");
            formatter.printInfo("─".repeat(65));
            System.out.println(analysis);
            formatter.printInfo("─".repeat(65));

            // Also show what would actually be selected
            Optional<TaskType> taskType = autoModelSelector.selectTaskTypeForPrompt(args);

            formatter.printInfo("\n📊 Selected Model:");
            if (taskType.isEmpty()) {
                formatter.printInfo("  • Using DEFAULT provider (general task)");
                AgentConfiguration config = configManager.getConfiguration();
                var llmConfig = config.getLlmConfigs().get(config.getDefaultProvider());
                if (llmConfig != null) {
                    formatter.printInfo("  • Model: " + config.getDefaultProvider() + " - " + llmConfig.getModel());
                }
            } else {
                AgentConfiguration config = configManager.getConfiguration();
                TaskModelConfig taskConfig = config.getTaskModels().get(taskType.get());
                if (taskConfig != null) {
                    formatter.printInfo("  • Task Type: " + taskType.get().getDisplayName());
                    formatter.printInfo("  • Model: " + taskConfig.getProvider() + " - " + taskConfig.getModel());
                } else {
                    formatter.printInfo("  • Task Type: " + taskType.get().getDisplayName());
                    formatter.printInfo("  • ⚠️  No task-specific model configured, using default");
                }
            }
            formatter.printInfo("");

        } catch (Exception e) {
            formatter.printError("Error during classification: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleMCP(String args) {
        String[] parts = args.split("\\s+", 2);
        String subcommand = parts.length > 0 ? parts[0] : "";

        switch (subcommand) {
            case "list" -> {
                formatter.printInfo("Connected MCP servers:");
                mcpClient.getConnectedServers().forEach(server ->
                        formatter.printSuccess("  - " + server));
            }
            case "tools" -> {
                if (parts.length < 2) {
                    formatter.printError("Usage: /mcp tools <server-name>");
                    formatter.printInfo("Use 'local' to see plugin tools: /mcp tools local");
                    return;
                }
                String serverName = parts[1];

                // Handle local tools (core + plugins)
                if ("local".equalsIgnoreCase(serverName)) {
                    formatter.printInfo("Tools from local server (core + plugins):");
                    var allTools = toolRegistry.getAllTools();
                    if (allTools.isEmpty()) {
                        formatter.printInfo("  No tools available");
                    } else {
                        allTools.forEach(tool ->
                                System.out.println("  - " + tool.getName() + ": " + tool.getDescription()));
                        formatter.printInfo("\nTotal: " + allTools.size() + " tools");
                    }
                    return;
                }

                // Handle external MCP servers
                try {
                    var tools = mcpClient.listTools(serverName);
                    formatter.printInfo("Tools from " + serverName + ":");
                    tools.forEach(tool ->
                            System.out.println("  - " + tool.getName() + ": " + tool.getDescription()));
                } catch (Exception e) {
                    formatter.printError("Error listing tools: " + e.getMessage());
                }
            }
            default -> formatter.printError("Usage: /mcp [list|tools <server-name>]");
        }
    }

    private void handlePlugin(String args) {
        String[] parts = args.split("\\s+", 2);
        String subcommand = parts.length > 0 ? parts[0] : "";

        switch (subcommand) {
            case "list" -> {
                formatter.printInfo("Loaded plugins:");
                var plugins = pluginManager.getAllPlugins();
                if (plugins.isEmpty()) {
                    formatter.printInfo("  No plugins loaded");
                } else {
                    plugins.forEach(plugin ->
                            System.out.println("  - " + plugin.getName() + " v" + plugin.getVersion() +
                                    " (" + plugin.getType() + "): " + plugin.getDescription() +
                                    " [" + plugin.getTools().size() + " tools]"));
                }
            }
            case "tools" -> {
                formatter.printInfo("All tools from plugins:");
                var allTools = pluginManager.getAllPluginTools();
                if (allTools.isEmpty()) {
                    formatter.printInfo("  No plugin tools loaded");
                } else {
                    allTools.forEach(tool ->
                            System.out.println("  - " + tool.getName() + ": " + tool.getDescription()));
                }
            }
            case "reload" -> {
                formatter.printInfo("Reloading plugins...");
                pluginManager.reloadAllPlugins();
                formatter.printSuccess("Plugins reloaded!");
            }
            default -> formatter.printError("Usage: /plugin [list|tools|reload]");
        }
    }

    private void handleSkill(String args) {
        String[] parts = args.split("\\s+", 2);
        String subcommand = parts.length > 0 ? parts[0] : "";

        switch (subcommand) {
            case "list" -> {
                formatter.printInfo("Available skills:");
                skillManager.getAllSkills().forEach(skill ->
                        System.out.println("  - " + skill.getName() + ": " + skill.getDescription()));
            }
            case "create" -> {
                formatter.printInfo("Skill creation wizard coming soon...");
                formatter.printInfo("For now, create skills manually in: ~/.t1-super-ai/skills/");
            }
            default -> formatter.printError("Usage: /skill [list|create]");
        }
    }

    private void handleAgent(String args) {
        String[] parts = args.split("\\s+", 2);
        String subcommand = parts.length > 0 ? parts[0] : "";

        switch (subcommand) {
            case "create" -> handleAgentCreate(parts.length > 1 ? parts[1] : "");
            case "list" -> handleAgentList();
            case "use" -> handleAgentUse(parts.length > 1 ? parts[1] : "");
            case "remove" -> handleAgentRemove(parts.length > 1 ? parts[1] : "");
            default -> formatter.printError("Usage: /agent [create <name>|list|use <name>|remove <name>]");
        }
    }

    private void handleAgentCreate(String agentName) {
        if (agentName.isEmpty()) {
            formatter.printError("Usage: /agent create <name>");
            formatter.printInfo("Example: /agent create dev-agent");
            return;
        }

        try {
            // Check if agent already exists
            if (agentManager.hasAgent(agentName)) {
                formatter.printError("Agent '" + agentName + "' already exists");
                formatter.printInfo("Use '/agent list' to see all agents");
                return;
            }

            // Check if CLI is available for interactive input
            if (cli == null) {
                formatter.printError("Interactive agent creation requires CLI support");
                formatter.printInfo("Creating agent with default settings...");
                createAgentWithDefaults(agentName);
                return;
            }

            // Delegate to CLI for interactive wizard
            cli.startAgentCreationWizard(agentName);

        } catch (Exception e) {
            formatter.printError("Error creating agent: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Create an agent with default settings (fallback)
     */
    private void createAgentWithDefaults(String agentName) {
        try {
            AgentConfiguration config = configManager.getConfiguration();
            LLMProvider defaultProvider = config.getDefaultProvider();
            var llmConfig = config.getLlmConfigs().get(defaultProvider);
            String defaultModel = llmConfig != null ? llmConfig.getModel() : "default";

            IndividualAgentConfig agentConfig = new IndividualAgentConfig(
                agentName,
                "General Purpose Agent",
                "A helpful AI assistant",
                defaultProvider,
                defaultModel
            );

            // Save configuration
            try {
                agentConfigService.saveAgentConfig(agentConfig);
            } catch (Exception e) {
                formatter.printError("Warning: Could not save agent configuration: " + e.getMessage());
            }

            // Create the agent
            Agent newAgent = agentManager.createAgent(agentName, orchestrator, agentConfig);

            formatter.printSuccess("✅ Agent '" + agentName + "' created with default settings!");
            formatter.printInfo("Provider: " + defaultProvider + " | Model: " + defaultModel);
            formatter.printInfo("💡 Use '/agent use " + agentName + "' to switch to this agent");

        } catch (Exception e) {
            formatter.printError("Error creating agent: " + e.getMessage());
        }
    }


    private void handleAgentUse(String agentName) {
        if (agentName.isEmpty()) {
            formatter.printError("Usage: /agent use <name>");
            formatter.printInfo("Use '/agent list' to see all agents");
            return;
        }

        try {
            // Check if agent exists
            if (!agentManager.hasAgent(agentName)) {
                formatter.printError("Agent '" + agentName + "' does not exist");
                formatter.printInfo("Use '/agent list' to see all agents");
                formatter.printInfo("Use '/agent create " + agentName + "' to create it");
                return;
            }

            // Check if already on this agent
            if (agentManager.getCurrentAgentName().equals(agentName)) {
                formatter.printInfo("Already using agent: " + agentName);
                return;
            }

            // Switch to the agent
            agentManager.switchToAgent(agentName);
            formatter.printSuccess("Switched to agent: " + agentName);

            // Update CLI to show new agent in prompt
            if (cli != null) {
                cli.updatePromptAgent(agentName);
            }

        } catch (Exception e) {
            formatter.printError("Error switching agent: " + e.getMessage());
        }
    }

    private void handleAgentList() {
        List<Agent> agents = agentManager.listAgents();

        if (agents.isEmpty()) {
            formatter.printInfo("No agents found");
            return;
        }

        formatter.printInfo("");
        formatter.printInfo("Active Agents (" + agents.size() + "):");
        formatter.printInfo("");

        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String currentAgentName = agentManager.getCurrentAgentName();

        for (Agent agent : agents) {
            String marker = agent.getName().equals(currentAgentName) ? " *" : "  ";
            String statusIcon = switch (agent.getStatus()) {
                case "active" -> "🟢";
                case "idle" -> "🟡";
                case "stopped" -> "🔴";
                default -> "⚪";
            };

            System.out.println(marker + statusIcon + " " + agent.getName() +
                " (" + agent.getStatus() + ")");

            // Show configuration if available
            if (agent.getConfig() != null) {
                IndividualAgentConfig config = agent.getConfig();
                System.out.println("    📋 Role: " + config.getRole());
                System.out.println("    📝 Context: " + config.getContext());
                System.out.println("    🔧 Provider: " + config.getProvider() + " | Model: " + config.getModel());
            }

            System.out.println("    Created: " + agent.getCreatedAt().format(timeFormatter));
            System.out.println("    Last Active: " + agent.getLastActiveAt().format(timeFormatter));
            System.out.println("    Conversations: " + agent.getConversationHistory().size());
            System.out.println();
        }

        formatter.printInfo("* indicates current agent");
        formatter.printInfo("");
    }

    private void handleAgentRemove(String agentName) {
        if (agentName.isEmpty()) {
            formatter.printError("Usage: /agent remove <name>");
            return;
        }

        try {
            // Confirm deletion
            System.out.print("Are you sure you want to remove agent '" + agentName + "'? (y/n): ");
            System.out.flush();
            String response = System.console() != null ?
                System.console().readLine() :
                new java.util.Scanner(System.in).nextLine();

            if (response == null || !response.trim().toLowerCase().startsWith("y")) {
                formatter.printInfo("Cancelled");
                return;
            }

            boolean removed = agentManager.removeAgent(agentName);

            if (removed) {
                formatter.printSuccess("Agent '" + agentName + "' removed");

                // If we switched back to master, update the prompt
                if (cli != null && agentManager.getCurrentAgentName().equals("master")) {
                    cli.updatePromptAgent("master");
                }
            } else {
                formatter.printError("Agent '" + agentName + "' not found");
            }

        } catch (IllegalArgumentException e) {
            formatter.printError(e.getMessage());
        } catch (Exception e) {
            formatter.printError("Error removing agent: " + e.getMessage());
        }
    }

    private void handleReload() {
        formatter.printInfo("Reloading session context from CHARACTER.md and USAGE.md...");
        orchestrator.reloadSessionContext();
    }

    private void clearScreen() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
        formatter.printBanner();
    }

    private void handleExit() {
        formatter.printInfo("Goodbye!");
        if (cli != null) {
            cli.stop();
        }
        System.exit(0);
    }
}

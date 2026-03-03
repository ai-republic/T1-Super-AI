package com.airepublic.t1.config;

// Removed unused InteractiveConfigCLI import
import com.airepublic.t1.model.AgentConfiguration;
import com.airepublic.t1.model.AgentConfiguration.LLMConfig;
import com.airepublic.t1.model.AgentConfiguration.LLMProvider;
import com.airepublic.t1.model.AgentConfiguration.MCPServerConfig;
import com.airepublic.t1.model.AgentConfiguration.TaskType;
import com.airepublic.t1.model.AgentConfiguration.TaskModelConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

@Slf4j
@Component
public class AgentConfigurationManager {
    // Use Paths.get() for cross-platform compatibility (Windows, Linux, macOS)
    private static final Path CONFIG_PATH = Paths.get(System.getProperty("user.home"), ".t1-super-ai");
    private static final String CONFIG_DIR = CONFIG_PATH.toString();
    private static final String CONFIG_FILE = CONFIG_PATH.resolve("config.json").toString();
    private final ObjectMapper objectMapper;
    private AgentConfiguration configuration;
    private final ApplicationContext applicationContext;

    public AgentConfigurationManager(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        this.objectMapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT);
        loadConfiguration();
    }

    public boolean isConfigured() {
        return configuration != null && configuration.getDefaultProvider() != null;
    }

    public AgentConfiguration getConfiguration() {
        return configuration;
    }

    public void loadConfiguration() {
        try {
            File configFile = new File(CONFIG_FILE);
            if (configFile.exists()) {
                configuration = objectMapper.readValue(configFile, AgentConfiguration.class);
                log.info("Configuration loaded from {}", CONFIG_FILE);
            } else {
                configuration = new AgentConfiguration();
            }
        } catch (IOException e) {
            log.error("Error loading configuration", e);
            configuration = new AgentConfiguration();
        }
    }

    public void saveConfiguration() {
        try {
            Path configDir = Paths.get(CONFIG_DIR);
            if (!Files.exists(configDir)) {
                Files.createDirectories(configDir);
            }
            objectMapper.writeValue(new File(CONFIG_FILE), configuration);
            log.info("Configuration saved to {}", CONFIG_FILE);
        } catch (IOException e) {
            log.error("Error saving configuration", e);
        }
    }

    public void runConfigurationWizard() {
        // Use Scanner-based wizard for configuration
        Scanner scanner = new Scanner(System.in);

        System.out.println("\n╔════════════════════════════════════════════════════╗");
        System.out.println("║  🤖 T1 Super AI Configuration Wizard 🛠️        ║");
        System.out.println("╚════════════════════════════════════════════════════╝\n");

        // LLM Provider Selection
        System.out.println("🔧 Select your LLM providers (you can configure multiple):");
        System.out.println("  Enter comma-separated numbers (e.g., '1,2' for OpenAI and Anthropic)");
        System.out.println("  1. 🟢 OpenAI");
        System.out.println("  2. 🟣 Anthropic");
        System.out.println("  3. 🦙 Ollama");
        System.out.println("  4. ⭐ All of the above");

        String providerInput = readNonEmptyString(scanner, "\n📝 Enter your choice (e.g., 1,2 or 4): ");

        // Parse provider selection
        boolean configureOpenAI = false;
        boolean configureAnthropic = false;
        boolean configureOllama = false;

        if (providerInput.trim().equals("4")) {
            // All providers
            configureOpenAI = true;
            configureAnthropic = true;
            configureOllama = true;
        } else {
            // Parse comma-separated values
            String[] selections = providerInput.split(",");
            for (String sel : selections) {
                try {
                    int choice = Integer.parseInt(sel.trim());
                    switch (choice) {
                        case 1: configureOpenAI = true; break;
                        case 2: configureAnthropic = true; break;
                        case 3: configureOllama = true; break;
                        case 4:
                            configureOpenAI = true;
                            configureAnthropic = true;
                            configureOllama = true;
                            break;
                        default:
                            System.out.println("⚠️  Invalid choice: " + choice + " (ignored)");
                    }
                } catch (NumberFormatException e) {
                    System.out.println("⚠️  Invalid input: " + sel + " (ignored)");
                }
            }
        }

        if (!configureOpenAI && !configureAnthropic && !configureOllama) {
            System.out.println("❌ No valid providers selected. Please run the wizard again.");
            return;
        }

        System.out.println("\n✅ Selected providers:");
        if (configureOpenAI) System.out.println("  • OpenAI");
        if (configureAnthropic) System.out.println("  • Anthropic");
        if (configureOllama) System.out.println("  • Ollama");

        // Configure OpenAI
        if (configureOpenAI) {
            System.out.println("\n🟢 ─── OpenAI Configuration ───");
            String apiKey = readNonEmptyString(scanner, "🔑 Enter OpenAI API Key: ");
            String model = readStringWithDefault(scanner,
                "🎯 Enter model name (default: gpt-4o): ", "gpt-4o");

            LLMConfig openAIConfig = new LLMConfig();
            openAIConfig.setApiKey(apiKey);
            openAIConfig.setModel(model);
            openAIConfig.setBaseUrl("https://api.openai.com/v1");
            configuration.getLlmConfigs().put(LLMProvider.OPENAI, openAIConfig);
            System.out.println("✅ OpenAI configured successfully!");
        }

        // Configure Anthropic
        if (configureAnthropic) {
            System.out.println("\n🟣 ─── Anthropic Configuration ───");
            String apiKey = readNonEmptyString(scanner, "🔑 Enter Anthropic API Key: ");
            String model = readStringWithDefault(scanner,
                "🎯 Enter model name (default: claude-3-5-sonnet-20241022): ",
                "claude-3-5-sonnet-20241022");

            LLMConfig anthropicConfig = new LLMConfig();
            anthropicConfig.setApiKey(apiKey);
            anthropicConfig.setModel(model);
            anthropicConfig.setBaseUrl("https://api.anthropic.com");
            configuration.getLlmConfigs().put(LLMProvider.ANTHROPIC, anthropicConfig);
            System.out.println("✅ Anthropic configured successfully!");
        }

        // Configure Ollama
        if (configureOllama) {
            System.out.println("\n🦙 ─── Ollama Configuration ───");
            String baseUrl = readStringWithDefault(scanner,
                "🌐 Enter Ollama base URL (default: http://localhost:11434): ",
                "http://localhost:11434");
            String model = readStringWithDefault(scanner,
                "🎯 Enter model name (default: llama3.2): ", "llama3.2");

            LLMConfig ollamaConfig = new LLMConfig();
            ollamaConfig.setBaseUrl(baseUrl);
            ollamaConfig.setModel(model);
            configuration.getLlmConfigs().put(LLMProvider.OLLAMA, ollamaConfig);
            System.out.println("✅ Ollama configured successfully!");
        }

        // Set default provider
        System.out.println("\n⭐ ─── Default Provider ───");
        int providerIndex = 1;
        if (configureOpenAI) System.out.println("  " + providerIndex++ + ". 🟢 OpenAI");
        if (configureAnthropic) System.out.println("  " + providerIndex++ + ". 🟣 Anthropic");
        if (configureOllama) System.out.println("  " + providerIndex++ + ". 🦙 Ollama");

        int maxChoice = providerIndex - 1;
        int defaultChoice = readIntWithValidation(scanner,
            "📌 Select default provider (1-" + maxChoice + "): ", 1, maxChoice);

        // Map choice to provider
        int currentIndex = 1;
        if (configureOpenAI && defaultChoice == currentIndex++) {
            configuration.setDefaultProvider(LLMProvider.OPENAI);
        } else if (configureAnthropic && defaultChoice == currentIndex++) {
            configuration.setDefaultProvider(LLMProvider.ANTHROPIC);
        } else if (configureOllama && defaultChoice == currentIndex) {
            configuration.setDefaultProvider(LLMProvider.OLLAMA);
        }
        System.out.println("✅ Default provider set to: " + configuration.getDefaultProvider());

        // Task-based Model Configuration
        System.out.println("\n🎯 ─── Task-Specific Model Configuration ───");
        System.out.println("Configure different models for different task types");
        System.out.println("Organized by category for easier configuration\n");
        boolean configureTaskModels = readYesNo(scanner,
            "💡 Do you want to configure task-specific models? (y/n, default: y): ", true);

        if (configureTaskModels) {
            // Group 1: Text & Reasoning Tasks
            System.out.println("\n╔════════════════════════════════════════════════════╗");
            System.out.println("║  📝 TEXT & REASONING TASKS                         ║");
            System.out.println("╚════════════════════════════════════════════════════╝");
            configureTaskType(scanner, TaskType.GENERAL_KNOWLEDGE, configureOpenAI, configureAnthropic, configureOllama);
            configureTaskType(scanner, TaskType.CODING, configureOpenAI, configureAnthropic, configureOllama);

            // Group 2: Audio Tasks
            System.out.println("\n╔════════════════════════════════════════════════════╗");
            System.out.println("║  🎤 AUDIO TASKS                                    ║");
            System.out.println("╚════════════════════════════════════════════════════╝");
            System.out.println("💡 Recommended: OpenAI Whisper for STT, ElevenLabs/OpenAI for TTS");
            configureTaskType(scanner, TaskType.SPEECH_TO_TEXT, configureOpenAI, configureAnthropic, configureOllama);
            configureTaskType(scanner, TaskType.TEXT_TO_SPEECH, configureOpenAI, configureAnthropic, configureOllama);

            // Group 3: Image Tasks
            System.out.println("\n╔════════════════════════════════════════════════════╗");
            System.out.println("║  🖼️  IMAGE TASKS                                    ║");
            System.out.println("╚════════════════════════════════════════════════════╝");
            System.out.println("💡 Analysis: GPT-4 Vision, Claude 3.5; Generation: DALL-E, Stable Diffusion");
            configureTaskType(scanner, TaskType.IMAGE_ANALYSIS, configureOpenAI, configureAnthropic, configureOllama);
            configureTaskType(scanner, TaskType.IMAGE_GENERATION, configureOpenAI, configureAnthropic, configureOllama);

            // Group 4: Video Tasks
            System.out.println("\n╔════════════════════════════════════════════════════╗");
            System.out.println("║  🎬 VIDEO TASKS                                    ║");
            System.out.println("╚════════════════════════════════════════════════════╝");
            System.out.println("💡 Analysis: GPT-4 Vision, Claude 3.5; Generation: Specialized video models");
            configureTaskType(scanner, TaskType.VIDEO_ANALYSIS, configureOpenAI, configureAnthropic, configureOllama);
            configureTaskType(scanner, TaskType.VIDEO_GENERATION, configureOpenAI, configureAnthropic, configureOllama);
        }

        // MCP Servers
        System.out.println("\n🔌 ─── MCP Servers (Model Context Protocol) ───");
        boolean configureMCP = readYesNo(scanner,
            "💬 Do you want to configure MCP servers? (y/n): ", false);

        while (configureMCP) {
            MCPServerConfig mcpConfig = new MCPServerConfig();

            String name = readNonEmptyString(scanner, "📛 Enter MCP server name: ");
            mcpConfig.setName(name);

            String transport = readStringWithValidation(scanner,
                "🚀 Enter transport type (stdio/http): ",
                new String[]{"stdio", "http"});
            mcpConfig.setTransport(transport);

            if ("stdio".equalsIgnoreCase(transport)) {
                String command = readNonEmptyString(scanner, "⚙️  Enter command to start server: ");
                mcpConfig.setCommand(command);
            } else {
                String url = readNonEmptyString(scanner, "🌐 Enter server URL: ");
                mcpConfig.setUrl(url);
            }

            configuration.getMcpServers().add(mcpConfig);
            System.out.println("✅ MCP server '" + name + "' added!");

            configureMCP = readYesNo(scanner, "➕ Add another MCP server? (y/n): ", false);
        }

        // System Settings
        System.out.println("\n🔒 ─── System Settings ───");

        boolean enableFS = readYesNo(scanner,
            "📁 Enable file system access? (y/n, default: y): ", true);
        configuration.getSystemSettings().setEnableFileSystem(enableFS);

        boolean enableWeb = readYesNo(scanner,
            "🌐 Enable web access? (y/n, default: y): ", true);
        configuration.getSystemSettings().setEnableWebAccess(enableWeb);

        boolean enableBash = readYesNo(scanner,
            "💻 Enable bash execution? (y/n, default: y): ", true);
        configuration.getSystemSettings().setEnableBashExecution(enableBash);

        saveConfiguration();

        System.out.println("\n╔════════════════════════════════════════════════════╗");
        System.out.println("║  ✅ Configuration Complete!                        ║");
        System.out.println("╚════════════════════════════════════════════════════╝");
        System.out.println("📂 Configuration saved to: " + CONFIG_FILE);
        System.out.println("💡 You can manually edit this file or run /config again.\n");
    }

    // ========== Task Configuration Helper Method ==========

    private void configureTaskType(Scanner scanner, TaskType taskType,
                                   boolean configureOpenAI, boolean configureAnthropic, boolean configureOllama) {
        System.out.println("\n📋 " + taskType.getDisplayName() + " - " + taskType.getDescription());

        // Show available providers
        int providerIdx = 1;
        System.out.println("Available providers:");
        if (configureOpenAI) System.out.println("  " + providerIdx++ + ". 🟢 OpenAI");
        if (configureAnthropic) System.out.println("  " + providerIdx++ + ". 🟣 Anthropic");
        if (configureOllama) System.out.println("  " + providerIdx++ + ". 🦙 Ollama");
        System.out.println("  " + providerIdx + ". ⏭️  Skip (use default provider)");

        int maxProviderChoice = providerIdx;
        int providerChoice = readIntWithValidation(scanner,
            "🔧 Select provider for " + taskType.getDisplayName() + " (1-" + maxProviderChoice + "): ",
            1, maxProviderChoice);

        // Skip if user chooses to use default
        if (providerChoice == maxProviderChoice) {
            System.out.println("⏭️  Skipping - will use default provider for " + taskType.getDisplayName());
            return;
        }

        // Map choice to provider
        LLMProvider selectedProvider = null;
        int currentIdx = 1;
        if (configureOpenAI && providerChoice == currentIdx++) {
            selectedProvider = LLMProvider.OPENAI;
        } else if (configureAnthropic && providerChoice == currentIdx++) {
            selectedProvider = LLMProvider.ANTHROPIC;
        } else if (configureOllama && providerChoice == currentIdx) {
            selectedProvider = LLMProvider.OLLAMA;
        }

        if (selectedProvider != null) {
            LLMConfig llmConfig = configuration.getLlmConfigs().get(selectedProvider);
            String defaultModel = llmConfig != null ? llmConfig.getModel() : "";

            // Provide model suggestions based on task type
            String modelSuggestion = getModelSuggestion(taskType, selectedProvider);
            if (!modelSuggestion.isEmpty()) {
                System.out.println("💡 Suggested model: " + modelSuggestion);
            }

            String model = readStringWithDefault(scanner,
                "🎯 Enter model name (default: " + defaultModel + "): ", defaultModel);

            TaskModelConfig taskConfig = new TaskModelConfig(selectedProvider, model);
            configuration.getTaskModels().put(taskType, taskConfig);
            System.out.println("✅ " + taskType.getDisplayName() + " configured with " +
                selectedProvider + " - " + model);
        }
    }

    private String getModelSuggestion(TaskType taskType, LLMProvider provider) {
        return switch (taskType) {
            case SPEECH_TO_TEXT -> switch (provider) {
                case OPENAI -> "whisper-1";
                default -> "";
            };
            case TEXT_TO_SPEECH -> switch (provider) {
                case OPENAI -> "tts-1 or tts-1-hd";
                default -> "";
            };
            case IMAGE_ANALYSIS -> switch (provider) {
                case OPENAI -> "gpt-4o or gpt-4-vision-preview";
                case ANTHROPIC -> "claude-3-5-sonnet-20241022";
                default -> "";
            };
            case IMAGE_GENERATION -> switch (provider) {
                case OPENAI -> "dall-e-3";
                default -> "";
            };
            case VIDEO_ANALYSIS -> switch (provider) {
                case OPENAI -> "gpt-4o";
                case ANTHROPIC -> "claude-3-5-sonnet-20241022";
                default -> "";
            };
            case CODING -> switch (provider) {
                case OPENAI -> "gpt-4o or o1-preview";
                case ANTHROPIC -> "claude-3-5-sonnet-20241022";
                case OLLAMA -> "codellama or deepseek-coder";
            };
            default -> "";
        };
    }

    // ========== Validation Helper Methods ==========

    private int readIntWithValidation(Scanner scanner, String prompt, int min, int max) {
        while (true) {
            System.out.print(prompt);
            try {
                String input = scanner.nextLine().trim();
                int value = Integer.parseInt(input);
                if (value >= min && value <= max) {
                    return value;
                }
                System.out.println("❌ Please enter a number between " + min + " and " + max);
            } catch (NumberFormatException e) {
                System.out.println("❌ Invalid input. Please enter a number between " + min + " and " + max);
            }
        }
    }

    private String readNonEmptyString(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            if (!input.isEmpty()) {
                return input;
            }
            System.out.println("❌ This field cannot be empty. Please try again.");
        }
    }

    private String readStringWithDefault(Scanner scanner, String prompt, String defaultValue) {
        System.out.print(prompt);
        String input = scanner.nextLine().trim();
        return input.isEmpty() ? defaultValue : input;
    }

    private String readStringWithValidation(Scanner scanner, String prompt, String[] validValues) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim().toLowerCase();
            for (String valid : validValues) {
                if (valid.equalsIgnoreCase(input)) {
                    return input;
                }
            }
            System.out.println("❌ Invalid input. Valid options: " + String.join(", ", validValues));
        }
    }

    private boolean readYesNo(Scanner scanner, String prompt, boolean defaultValue) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim().toLowerCase();

            if (input.isEmpty()) {
                return defaultValue;
            }

            if (input.equals("y") || input.equals("yes")) {
                return true;
            } else if (input.equals("n") || input.equals("no")) {
                return false;
            }

            System.out.println("❌ Please enter 'y' for yes or 'n' for no");
        }
    }

    public void updateProvider(LLMProvider provider) {
        configuration.setDefaultProvider(provider);
        saveConfiguration();
    }

    public void updateModel(String model) {
        LLMProvider currentProvider = configuration.getDefaultProvider();
        LLMConfig llmConfig = configuration.getLlmConfigs().get(currentProvider);

        if (llmConfig == null) {
            throw new IllegalStateException("Current provider " + currentProvider + " is not configured");
        }

        llmConfig.setModel(model);
        saveConfiguration();
    }

    public String getCurrentModel() {
        LLMProvider currentProvider = configuration.getDefaultProvider();
        LLMConfig llmConfig = configuration.getLlmConfigs().get(currentProvider);

        if (llmConfig == null) {
            return "No model configured";
        }

        return llmConfig.getModel();
    }
}

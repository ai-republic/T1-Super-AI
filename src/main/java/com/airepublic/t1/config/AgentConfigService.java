package com.airepublic.t1.config;

import com.airepublic.t1.model.IndividualAgentConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service for managing individual agent configurations.
 * Stores agent configs in ~/.t1-super-ai/agents/ directory.
 * Each agent has its own folder containing: config.json, CHARACTER.md, and USAGE.md
 */
@Slf4j
@Component
public class AgentConfigService {
    private static final Path AGENTS_DIR = Paths.get(System.getProperty("user.home"), ".t1-super-ai", "agents");
    private final ObjectMapper objectMapper;

    public AgentConfigService() {
        this.objectMapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .registerModule(new JavaTimeModule());

        // Ensure agents directory exists
        try {
            if (!Files.exists(AGENTS_DIR)) {
                Files.createDirectories(AGENTS_DIR);
                log.info("Created agents directory: {}", AGENTS_DIR);
            }
        } catch (IOException e) {
            log.error("Error creating agents directory", e);
        }
    }

    /**
     * Create a new agent folder with all required files
     */
    public void createAgentFolder(String agentName) throws IOException {
        Path agentFolder = getAgentFolder(agentName);
        if (Files.exists(agentFolder)) {
            log.warn("Agent folder already exists: {}", agentFolder);
            return;
        }

        Files.createDirectories(agentFolder);
        log.info("Created agent folder: {}", agentFolder);
    }

    /**
     * Get the folder path for a specific agent
     */
    public Path getAgentFolder(String agentName) {
        return AGENTS_DIR.resolve(agentName);
    }

    /**
     * Get the CHARACTER.md file path for an agent
     */
    public Path getCharacterMdPath(String agentName) {
        return getAgentFolder(agentName).resolve("CHARACTER.md");
    }

    /**
     * Get the USAGE.md file path for an agent
     */
    public Path getUsageMdPath(String agentName) {
        return getAgentFolder(agentName).resolve("USAGE.md");
    }

    /**
     * Create CHARACTER.md file for an agent from hatching responses
     */
    public void createCharacterMd(String agentName, Map<String, String> hatchData) throws IOException {
        Path characterFile = getCharacterMdPath(agentName);

        String agentRole = hatchData.getOrDefault("agent_role", "AI Assistant");
        String agentPurpose = hatchData.getOrDefault("agent_purpose", "General purpose assistance");
        String agentPersonality = hatchData.getOrDefault("agent_personality", "Professional and helpful");
        String communicationStyle = hatchData.getOrDefault("communication_style", "Clear and concise");
        String specialties = hatchData.getOrDefault("specialties", "None specified");
        String constraints = hatchData.getOrDefault("constraints", "None specified");

        String content = String.format("""
                # Agent Character Profile: %s

                ## Identity
                - **Name**: %s
                - **Role**: %s
                - **Purpose**: %s

                ## Personality
                %s

                ## Communication Style
                %s

                ## Specialties
                %s

                ## Constraints
                %s

                ## Created
                Generated on: %s
                """,
                agentName,
                agentName,
                agentRole,
                agentPurpose,
                agentPersonality,
                communicationStyle,
                specialties,
                constraints,
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        );

        Files.writeString(characterFile, content, StandardCharsets.UTF_8);
        log.info("Created CHARACTER.md for agent: {}", agentName);
    }

    /**
     * Create USAGE.md file for an agent
     */
    public void createUsageMd(String agentName, IndividualAgentConfig config) throws IOException {
        Path usageFile = getUsageMdPath(agentName);

        String content = String.format("""
                # %s - Usage Guide

                ## Overview
                - **Role**: %s
                - **Context**: %s

                ## Configuration
                - **Provider**: %s
                - **Model**: %s

                ## How to Use This Agent

                ### Activation
                ```
                /agent use %s
                ```

                ### Best Used For
                %s

                ### Example Interactions

                1. **Task Type**: [Describe task]
                   - **Input**: [Example input]
                   - **Expected Output**: [What to expect]

                2. **Task Type**: [Describe task]
                   - **Input**: [Example input]
                   - **Expected Output**: [What to expect]

                ## Tips
                - Be specific with your requests
                - Provide context when needed
                - Use /reload if CHARACTER.md is updated

                ## Switching Agents
                To switch to another agent:
                ```
                /agent list          # See all available agents
                /agent use <name>    # Switch to a different agent
                ```

                ## Last Updated
                %s
                """,
                agentName,
                config.getRole(),
                config.getContext(),
                config.getProvider(),
                config.getModel(),
                agentName,
                config.getContext(),
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        );

        Files.writeString(usageFile, content, StandardCharsets.UTF_8);
        log.info("Created USAGE.md for agent: {}", agentName);
    }

    /**
     * Read CHARACTER.md content for an agent
     */
    public String readCharacterMd(String agentName) throws IOException {
        Path characterFile = getCharacterMdPath(agentName);
        if (!Files.exists(characterFile)) {
            return null;
        }
        return Files.readString(characterFile, StandardCharsets.UTF_8);
    }

    /**
     * Read USAGE.md content for an agent
     */
    public String readUsageMd(String agentName) throws IOException {
        Path usageFile = getUsageMdPath(agentName);
        if (!Files.exists(usageFile)) {
            return null;
        }
        return Files.readString(usageFile, StandardCharsets.UTF_8);
    }

    /**
     * Save an agent configuration in its folder
     */
    public void saveAgentConfig(IndividualAgentConfig config) throws IOException {
        config.updateLastModified();
        Path agentFolder = getAgentFolder(config.getName());

        // Create folder if it doesn't exist
        if (!Files.exists(agentFolder)) {
            Files.createDirectories(agentFolder);
        }

        Path configFile = agentFolder.resolve("config.json");
        objectMapper.writeValue(configFile.toFile(), config);
        log.info("Saved agent configuration: {}", configFile);
    }

    /**
     * Load an agent configuration by name (supports both folder and legacy formats)
     */
    public IndividualAgentConfig loadAgentConfig(String agentName) throws IOException {
        Path configFile = getAgentFolder(agentName).resolve("config.json");

        // Legacy support: check for old format (agentName.json in agents root)
        if (!Files.exists(configFile)) {
            Path legacyFile = AGENTS_DIR.resolve(agentName + ".json");
            if (Files.exists(legacyFile)) {
                log.info("Found legacy config for {}, migrating to folder structure", agentName);
                IndividualAgentConfig config = objectMapper.readValue(legacyFile.toFile(), IndividualAgentConfig.class);

                // Migrate to new structure
                createAgentFolder(agentName);
                saveAgentConfig(config);
                Files.delete(legacyFile);

                return config;
            }
            throw new IOException("Agent configuration not found: " + agentName);
        }

        return objectMapper.readValue(configFile.toFile(), IndividualAgentConfig.class);
    }

    /**
     * Check if an agent configuration exists
     */
    public boolean agentConfigExists(String agentName) {
        Path configFile = getAgentFolder(agentName).resolve("config.json");
        Path legacyFile = AGENTS_DIR.resolve(agentName + ".json");
        return Files.exists(configFile) || Files.exists(legacyFile);
    }

    /**
     * List all agent configurations (supports both folder and legacy formats)
     */
    public List<IndividualAgentConfig> listAllAgentConfigs() {
        List<IndividualAgentConfig> configs = new ArrayList<>();
        File agentsDir = AGENTS_DIR.toFile();

        if (!agentsDir.exists() || !agentsDir.isDirectory()) {
            return configs;
        }

        // Check for new folder-based structure
        File[] folders = agentsDir.listFiles(File::isDirectory);
        if (folders != null) {
            for (File folder : folders) {
                File configFile = new File(folder, "config.json");
                if (configFile.exists()) {
                    try {
                        IndividualAgentConfig config = objectMapper.readValue(configFile, IndividualAgentConfig.class);
                        configs.add(config);
                    } catch (IOException e) {
                        log.error("Error loading agent config from folder: {}", folder.getName(), e);
                    }
                }
            }
        }

        // Also check for legacy format (JSON files in root)
        File[] files = agentsDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files != null) {
            for (File file : files) {
                try {
                    IndividualAgentConfig config = objectMapper.readValue(file, IndividualAgentConfig.class);
                    configs.add(config);
                } catch (IOException e) {
                    log.error("Error loading agent config from file: {}", file.getName(), e);
                }
            }
        }

        return configs;
    }

    /**
     * Delete an agent configuration and its folder
     */
    public boolean deleteAgentConfig(String agentName) {
        Path agentFolder = getAgentFolder(agentName);

        try {
            if (Files.exists(agentFolder)) {
                // Delete all files in the folder
                Files.walk(agentFolder)
                     .sorted((p1, p2) -> -p1.compareTo(p2)) // Reverse order to delete files before folders
                     .forEach(path -> {
                         try {
                             Files.delete(path);
                         } catch (IOException e) {
                             log.error("Error deleting file: {}", path, e);
                         }
                     });
                log.info("Deleted agent folder: {}", agentName);
                return true;
            }

            // Also check legacy format
            Path legacyFile = AGENTS_DIR.resolve(agentName + ".json");
            if (Files.exists(legacyFile)) {
                boolean deleted = Files.deleteIfExists(legacyFile);
                if (deleted) {
                    log.info("Deleted legacy agent configuration: {}", agentName);
                }
                return deleted;
            }

        } catch (IOException e) {
            log.error("Error deleting agent configuration: {}", agentName, e);
            return false;
        }

        return false;
    }

    /**
     * Get the agents directory path
     */
    public Path getAgentsDirectory() {
        return AGENTS_DIR;
    }
}

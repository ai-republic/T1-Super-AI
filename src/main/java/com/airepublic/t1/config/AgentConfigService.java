package com.airepublic.t1.config;

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

import org.springframework.stereotype.Component;

import com.airepublic.t1.model.AgentConfiguration.LLMProvider;
import com.airepublic.t1.model.IndividualAgentConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import lombok.extern.slf4j.Slf4j;

/**
 * Service for managing individual agent configurations.
 * Stores agent configs in ~/.t1-super-ai/agents/ directory.
 * Each agent has its own folder containing: config.json, CHARACTER.md, and USAGE.md
 */
@Slf4j
@Component
public class AgentConfigService {
    private static final Path AGENTS_DIR = Paths.get(System.getProperty("user.home"), ".t1-super-ai", "agents");
    private static final Path WORKSPACE_DIR = Paths.get(System.getProperty("user.home"), ".t1-super-ai");
    private final ObjectMapper objectMapper;

    /**
     * Data class to hold user information from USER.md
     */
    public static class UserInfo {
        public String userName;
        public String userPronouns;
        public String userFocus;
        public String defaultAgent;

        public UserInfo(final String userName, final String userPronouns, final String userFocus, final String defaultAgent) {
            this.userName = userName;
            this.userPronouns = userPronouns;
            this.userFocus = userFocus;
            this.defaultAgent = defaultAgent;
        }
    }

    public AgentConfigService() {
        objectMapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT)
                .registerModule(new JavaTimeModule());

        // Ensure agents directory exists
        try {
            if (!Files.exists(AGENTS_DIR)) {
                Files.createDirectories(AGENTS_DIR);
                log.info("Created agents directory: {}", AGENTS_DIR);
            }
        } catch (final IOException e) {
            log.error("Error creating agents directory", e);
        }
    }

    /**
     * Update the default agent in USER.md
     * Called when switching agents to remember the last used agent
     */
    public void updateDefaultAgent(final String agentName) throws IOException {
        final Path userMdFile = WORKSPACE_DIR.resolve("USER.md");

        if (!Files.exists(userMdFile)) {
            log.warn("USER.md not found at: {}. Cannot update default agent.", userMdFile);
            return;
        }

        try {
            final String content = Files.readString(userMdFile, StandardCharsets.UTF_8);
            final String[] lines = content.split("\n");
            final StringBuilder updatedContent = new StringBuilder();

            for (final String line : lines) {
                if (line.trim().startsWith("**Default Agent:**")) {
                    updatedContent.append("**Default Agent:** ").append(agentName).append("\n");
                } else {
                    updatedContent.append(line).append("\n");
                }
            }

            Files.writeString(userMdFile, updatedContent.toString(), StandardCharsets.UTF_8);
            log.info("✅ Updated default agent in USER.md to: {}", agentName);

        } catch (final IOException e) {
            log.error("Error updating default agent in USER.md", e);
            throw e;
        }
    }

    /**
     * Read user information from the USER.md file in .t1-super-ai directory
     * This file is created during initial workspace setup/hatching
     */
    public UserInfo readUserInfo() {
        // Default values if file doesn't exist or can't be parsed
        String userName = System.getProperty("user.name", "User");
        String userPronouns = "they/them";
        String userFocus = "Software development";
        String defaultAgent = null;

        final Path userMdFile = WORKSPACE_DIR.resolve("USER.md");

        if (!Files.exists(userMdFile)) {
            log.warn("USER.md not found at: {}. Using defaults.", userMdFile);
            return new UserInfo(userName, userPronouns, userFocus, defaultAgent);
        }

        try {
            final String content = Files.readString(userMdFile, StandardCharsets.UTF_8);
            final String[] lines = content.split("\n");

            for (final String line : lines) {
                final String trimmed = line.trim();

                if (trimmed.startsWith("**Name:**")) {
                    userName = trimmed.substring("**Name:**".length()).trim();
                } else if (trimmed.startsWith("**Pronouns:**")) {
                    userPronouns = trimmed.substring("**Pronouns:**".length()).trim();
                } else if (trimmed.startsWith("**Work Focus:**")) {
                    userFocus = trimmed.substring("**Work Focus:**".length()).trim();
                } else if (trimmed.startsWith("**Default Agent:**")) {
                    final String extracted = trimmed.substring("**Default Agent:**".length()).trim();
                    // Only set if not empty
                    if (!extracted.isEmpty()) {
                        defaultAgent = extracted;
                    }
                }
            }

            log.info("📋 Read user info from USER.md: name={}, pronouns={}, focus={}, defaultAgent='{}'",
                    userName, userPronouns, userFocus, defaultAgent != null ? defaultAgent : "(not set)");

        } catch (final IOException e) {
            log.error("Error reading USER.md file. Using defaults.", e);
        }

        return new UserInfo(userName, userPronouns, userFocus, defaultAgent);
    }

    /**
     * Create a new agent folder with all required files
     */
    public void createAgentFolder(final String agentName) throws IOException {
        final Path agentFolder = getAgentFolder(agentName);
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
    public Path getAgentFolder(final String agentName) {
        return AGENTS_DIR.resolve(agentName);
    }

    /**
     * Get the CHARACTER.md file path for an agent
     */
    public Path getCharacterMdPath(final String agentName) {
        return getAgentFolder(agentName).resolve("CHARACTER.md");
    }

    /**
     * Get the USAGE.md file path for an agent
     */
    public Path getUsageMdPath(final String agentName) {
        return getAgentFolder(agentName).resolve("USAGE.md");
    }


    /**
     * Update CHARACTER.md file for an agent by merging with existing data
     */
    public void updateCharacterMd(final IndividualAgentConfig config) throws IOException {
        final String agentName = config.getName();

        if (agentName == null || agentName.trim().isEmpty()) {
            throw new IllegalArgumentException("Agent name is required to update CHARACTER.md");
        }

        final Path characterFile = getCharacterMdPath(agentName);

        if (!Files.exists(characterFile)) {
            throw new IOException("CHARACTER.md not found for agent: " + agentName);
        }

        // Read existing content and parse it
        final String existingContent = Files.readString(characterFile, StandardCharsets.UTF_8);
        // find the index of "## General behavioral guidelines" section
        final int guidelinesIndex = existingContent.indexOf("## General behavioral guidelines");
        final String guidelinesSection = guidelinesIndex != -1 ? existingContent.substring(guidelinesIndex) : "";
        createCharacterMd(config, guidelinesSection);

        log.info("Updated CHARACTER.md for agent: {}", agentName);
    }

    /**
     * Create USAGE.md file for an agent
     */
    public void createUsageMd(final String agentName, final IndividualAgentConfig config) throws IOException {
        final Path usageFile = getUsageMdPath(agentName);

        final String content = getUsageContent();

        Files.writeString(usageFile, content, StandardCharsets.UTF_8);
        log.info("Created USAGE.md for agent: {}", agentName);
    }

    public static String getUserTemplateContent() {
        return "# 👤 User Profile\n\n" +
                "**Status:** active\n" +
                "**Last Updated:** {{TIMESTAMP}}\n\n" +
                "## User Information\n\n" +
                "**Name:** {{USER_NAME}}\n" +
                "**Pronouns:** {{USER_PRONOUNS}}\n" +
                "**Work Focus:** {{USER_FOCUS}}\n" +
                "**Timezone:** {{USER_TIMEZONE}}\n\n" +
                "## Active Agent\n\n" +
                "**Default Agent:** {{DEFAULT_AGENT}}\n\n" +
                "---\n\n" +
                "_This file contains user-specific information that is shared across all agents._\n" +
                "_The default agent is automatically loaded when the application starts._\n";
    }

    public String getCharacterProfileTemplate() {
        return "# 🤖 Agent Character Profile\n\n" +
                "**Status:** active\n" +
                "**Date Created:** {{CREATED_DATE}}\n" +
                "**Last Modified:** {{MODIFIED_DATE}}\n\n" +
                "## 🎭 Agent Identity\n\n" +
                "**Agent Name:** {{NAME}}\n" +
                "**Agent Role:** {{ROLE}}\n" +
                "**Purpose:** {{PURPOSE}}\n" +
                "**Specialization:** {{SPECIALIZATION}}\n\n" +
                "## 🤖 LLM Configuration\n\n" +
                "**Provider:** {{PROVIDER}}\n" +
                "**Model:** {{MODEL}}\n\n" +
                "## 💬 Communication Profile\n\n" +
                "**Style:** {{STYLE}}\n" +
                "**Personality:** {{PERSONALITY}}\n" +
                "**Emoji Preference:** {{EMOJI_PREFERENCE}}\n\n";
    }

    public static String getCharacterBehaviorTemplate() {
        return "**Your workspace**: Your workspace where you find all configurations, including /agents, /plugins, /mcp-servers, /skills, USAGE.MD, USER.md and credentials.json, is in the users home directory '.t1-super-ai' folder.\n\n" +
                "## General behavioral guidelines\n" +
                "- Always align with the user's preferences and work focus (see USER.md)\n" +
                "- Do not ask too many questions unless you are unsure\n" +
                "- Ask if you need credentials to access resources unless they are already defined in your 'credentials.json' file.\n\n" +
                "## MCP Servers\n" +
                "You are connected to a local MCP server that provides core tools that give you read/write access, bash and cmd to execute commands, web_search to search the web.\n" +
                "External MCP server configurations can be found under the users home directory in the folder 'mcpservers'\n\n" +
                "## Skills\n" +
                "Custom skills can be found in your workspace in the folder 'skills'\n\n" +
                "## Plugins and tools\n" +
                "Custom plugins (each containing multiple tools) can be found in your workspace in the folder 'plugins'. Each tool has a 'plugins.json' file defining the tool and a README.md how to use each tool.\n\n" +
                "---\n\n" +
                "For more information on configuring tools, plugins, MCP servers, and skills, please refer to the USAGE.md and CONFIGURATION_GUIDE.md files in your workspace.\n\n" +
                "---\n\n" +
                "_This file is automatically loaded as context for every session._\n";
    }

    /**
     * Create CHARACTER.md from template with filled values
     * Note: User information is stored in USER.md in workspace root, not here
     */
    public void createCharacterMd(final IndividualAgentConfig config, final String behaviorContent) throws IOException {
        final Path characterFile = getCharacterMdPath(config.getName());
        final String template = getCharacterProfileTemplate();

        final StringBuilder content = new StringBuilder().append(template
                .replace("{{STATUS}}", config.getStatus() != null ? config.getStatus() : "active")
                .replace("{{CREATED_DATE}}", config.getCreatedAt() != null ? config.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .replace("{{MODIFIED_DATE}}", config.getLastModifiedAt() != null ? config.getLastModifiedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .replace("{{NAME}}", config.getName() != null ? config.getName() : "master")
                .replace("{{ROLE}}", config.getRole() != null ? config.getRole() : "General Purpose Agent")
                .replace("{{PURPOSE}}", config.getPurpose() != null ? config.getPurpose() : "General assistance")
                .replace("{{SPECIALIZATION}}", config.getSpecialization() != null ? config.getSpecialization() : "General assistance")
                .replace("{{PROVIDER}}", config.getProvider() != null ? config.getProvider().name() : "OPENAI")
                .replace("{{MODEL}}", config.getModel() != null ? config.getModel() : "gpt-4")
                .replace("{{STYLE}}", config.getPersonality() != null ? config.getPersonality() : "Professional and Friendly")
                .replace("{{PERSONALITY}}", config.getPersonality() != null ? config.getPersonality() : "Thorough and detail-oriented")
                .replace("{{EMOJI_PREFERENCE}}", config.getEmojiPreference() != null ? config.getEmojiPreference() : "Sparingly"));
        content.append("\n\n").append(behaviorContent);

        Files.writeString(characterFile, content.toString(), StandardCharsets.UTF_8);
        log.info("✅ Created CHARACTER.md with agent profile for '{}' (provider: {}, model: {})", config.getName(), config.getProvider(), config.getModel());
    }

    /**
     * Read CHARACTER.md content for an agent
     */
    public String readCharacterMd(final String agentName) throws IOException {
        final Path characterFile = getCharacterMdPath(agentName);
        if (!Files.exists(characterFile)) {
            return null;
        }
        return Files.readString(characterFile, StandardCharsets.UTF_8);
    }

    /**
     * Parse CHARACTER.md and create IndividualAgentConfig from it
     * This replaces reading from config.json
     */
    public IndividualAgentConfig loadIndividualAgentConfig(final String agentName) throws IOException {
        final String content = readCharacterMd(agentName);
        if (content == null) {
            throw new IOException("CHARACTER.md not found for agent: " + agentName);
        }

        // Parse fields from CHARACTER.md
        String status = null;
        String dateCreated = null;
        String lastUpdated = null;
        String name = agentName;
        String role = null;
        String purpose = null;
        String specialization = null;
        String provider = null;
        String model = null;
        String style = null;
        String personality = null;
        String emojiPreference = null;

        final String[] lines = content.split("\n");
        for (String line : lines) {
            line = line.trim();

            if (line.startsWith("**Status:**")) {
                status = line.substring("**Status:**".length()).trim();
            } else if (line.startsWith("**Date Created:**")) {
                dateCreated = line.substring("**Date Created:**".length()).trim();
            } else if (line.startsWith("**Last Modified:**")) {
                lastUpdated = line.substring("**Last Modified:**".length()).trim();
            } else if (line.startsWith("**Agent Name:**")) {
                name = line.substring("**Agent Name:**".length()).trim();
            } else if (line.startsWith("**Role:**")) {
                role = line.substring("**Role:**".length()).trim();
            } else if (line.startsWith("**Purpose:**")) {
                purpose = line.substring("**Purpose:**".length()).trim();
            } else if (line.startsWith("**Specialization:**")) {
                specialization = line.substring("**Specialization:**".length()).trim();
            } else if (line.startsWith("**Provider:**")) {
                provider = line.substring("**Provider:**".length()).trim();
            } else if (line.startsWith("**Model:**")) {
                model = line.substring("**Model:**".length()).trim();
            } else if (line.startsWith("**Style:**")) {
                style = line.substring("**Style:**".length()).trim();
            } else if (line.startsWith("**Personality:**")) {
                personality = line.substring("**Personality:**".length()).trim();
            } else if (line.startsWith("**Emoji Preference:**")) {
                emojiPreference = line.substring("**Emoji Preference:**".length()).trim();
            }
        }

        // Create config from parsed data
        final IndividualAgentConfig config = new IndividualAgentConfig();
        config.setStatus(status != null ? status : "active");
        config.setCreatedAt(dateCreated != null ? LocalDateTime.parse(dateCreated, DateTimeFormatter.ISO_LOCAL_DATE_TIME) : LocalDateTime.now());
        config.setLastModifiedAt(lastUpdated != null ? LocalDateTime.parse(lastUpdated, DateTimeFormatter.ISO_LOCAL_DATE_TIME) : LocalDateTime.now());
        config.setName(name);
        config.setRole(role != null ? role : "General Purpose Agent");
        config.setPurpose(purpose != null ? purpose : "AI Assistant");
        config.setSpecialization(specialization != null ? specialization : "General assistance");
        config.setProvider(provider != null ? LLMProvider.valueOf(provider.toUpperCase()) : LLMProvider.OPENAI);
        config.setModel(model != null ? model : "gpt-4");
        config.setStyle(style != null ? style : "Professional and Friendly");
        config.setPersonality(personality != null ? personality : "Thorough and detail-oriented");
        config.setEmojiPreference(emojiPreference != null ? emojiPreference : "Sparingly");

        try {
            config.setProvider(com.airepublic.t1.model.AgentConfiguration.LLMProvider.valueOf(provider.toUpperCase()));
        } catch (final IllegalArgumentException e) {
            log.warn("Invalid provider '{}' in CHARACTER.md for agent '{}', using OPENAI", provider, agentName);
            config.setProvider(com.airepublic.t1.model.AgentConfiguration.LLMProvider.OPENAI);
        }

        config.setModel(model);

        log.info("📋 Parsed config from CHARACTER.md for '{}': provider={}, model={}", agentName, provider, model);
        return config;
    }

    public static String getUsageContent() {
        return """
                # T1 Super AI - Usage Guide

                **Location**: `~/.t1-super-ai/`

                ## Agents
                You can utilize others agents or create multiple agents with different roles and purposes. This allows you to have specialized agents for different tasks or domains. For example, you could have one agent focused on data analysis and another focused on software development. Each agent can have its own unique set of tools, skills, and plugins that are tailored to its specific role.
                First check if an agent exists that fits a task. You can use the follow tools to create, update, communicate and collaborate:
                - **list_agents**: to retrieve available agents with their name and purpose. Choose one that fits best or otherwise you have the option to create a specilized agent for the task.
                - **create_agent**: create a new agent by defining the agent's name, role, purpose, and any specific tools or skills it should have access to. Once created, the agent will be available for communication and task delegation.
                - **update_agent_character**: adjust an agent's role, purpose, communication style, personality, specialities and constraints as needed. This allows you to adapt and evolve the agents over time based on changing requirements or new information.
                - **send_message_to_agent**: communicate and collaborate with other agents. This allows you to leverage the expertise of different agents and get assistance on tasks that may be outside of your own capabilities.

                ## Plugins (5 types)
                Plugins are a collection of one or more tools.
                Plugins are stored in the users home folder under '.t1-super-ai/plugins/<plugin-name>'.
                Each plugin must have a 'plugin.json' as configuration file following the json structure of the examples below and a README.md explaining their usage.
                Plugins can be written in Java (JAR or Maven), Python, Javascript (node, npm, next.js) or bash shell script.
                If tools need credentials store them in the '.t1-super-ai/credentials.json' file following the naming and structuring instructions as defined CREDENTIALS section in this document.

                ### 1. JAR Plugin
                A plugin can be a downloaded Java JAR file. Place the JAR in the correctly named plugin folder:

                my-plugin/
                └─ plugin.jar

                Execute the JAR using:
                * if no executable is specified:
                    **Run tool**: java -jar <jarPath> -D<property-key1=property-value1> -Dproperty-key2=property-value2> -D<property-keyX=property-valueX> <argument1> <argument2>
                * if a executable is specified:
                    **Run tool**: java -cp <jarPath> <executable> -D<property-key1=property-value1> -Dproperty-key2=property-value2> -D<property-keyX=property-valueX> <argument1> <argument2>

                Add neccessary properties with -D<property-key=property-value>. See inputSchema->properties and inputSchema->requiredProperties.
                Add necessary arguments needed by the JAR. See inputSchema->arguments and inputSchema->requiredArguments.

                Example configuration 'plugin.json' structure:
                ```json
                {
                  "name": "database-plugin",
                  "enabled": true,
                  "type": "JAVA",
                  "jarPath": "plugin.jar",
                  "tools": {
                    "execute_sql": {
                      "description": "Use for SQL execution on a database when you have the SQL as text",
                      "executable": "com.plugin.DatabaseExecuteSQLPlugin", //optional, must only be specified if jar contains multiple main-classes
                      "inputSchema": {
                        "type": "object",
                        "properties": {
                          "jdbcUrl": {"type": "string", "description": "JDBC URL connection string"},
                          "username": {"type": "string", "description": "Database user name"},
                          "password": {"type": "string", "description": "Database user password"},
                          "sql": {"type": "string", "description": "SQL to execute"}
                        },
                        "requiredProperties": ["jdbcUrl", "username", "password", "sql"],
                        "arguments": {
                          "result-file": "Path to the file to store any results",
                          "verbose": "Log more info"
                        },
                        "requiredArguments": ["result-file"]
                      }
                    },
                    "execute_sql_file": {
                      "description": "Use for SQL execution on a database when you have the SQL as file",
                      "executable": "com.plugin.DatabaseExecuteSQLFilePlugin", //optional, must only be specified if jar contains multiple main-classes
                      "inputSchema": {
                        "type": "object",
                        "properties": {
                          "jdbcUrl": {"type": "string", "description": "JDBC URL connection string"},
                          "username": {"type": "string", "description": "Database user name"},
                          "password": {"type": "string", "description": "Database user password"},
                          "file": {"type": "string", "description": "Path to SQL file to execute"}
                        },
                        "requiredProperties": ["jdbcUrl", "username", "password", "sql"],
                        "arguments": {
                          "result-file": "Path to the file to store any results",
                          "verbose": "Log more info"
                        },
                        "requiredArguments": ["result-file"]
                      }
                    }
                  }
                }
                ```

                ### 2. MAVEN Project Plugin
                If writing a new plugin you can use Java and Maven.
                Use Maven’s standard folder structure:

                my-plugin/
                ├─ pom.xml
                ├─ src/main/java/com/plugin/example/Plugin.java
                ├─ src/main/resources/com/application.properties
                ├─ src/test/java/com/plugin/example/PluginTest.java
                └─ src/test/resources/application-test.properties

                **Maven source folder**: src/main/java
                **Build**: mvn clean package
                **Build output**: target/

                Execute the JAR using:
                * if no executable is specified:
                    **Run tool**: java -jar <jarPath> -D<property-key1=property-value1> -Dproperty-key2=property-value2> -D<property-keyX=property-valueX> <argument1> <argument2>
                * if a executable is specified:
                    **Run tool**: java -cp <jarPath> <executable> -D<property-key1=property-value1> -Dproperty-key2=property-value2> -D<property-keyX=property-valueX> <argument1> <argument2>

                Add neccessary properties with -D<property-key=property-value>. See inputSchema->properties and inputSchema->requiredProperties.
                Add necessary arguments needed by the JAR. See inputSchema->arguments and inputSchema->requiredArguments.

                Example configuration 'plugin.json' structure:
                ```json
                {
                  "name": "database-plugin",
                  "enabled": true,
                  "type": "JAVA",
                  "jarPath": "target/my-plugin-1.0-SNAPSHOT.jar",
                  "tools": {
                    "execute_sql": {
                      "description": "Use for SQL execution on a database when you have the SQL as text",
                      "executable": "com.plugin.DatabaseExecuteSQLTool", //optional, must only be specified if jar contains multiple main-classes
                      "inputSchema": {
                        "type": "object",
                        "properties": {
                          "jdbcUrl": {"type": "string", "description": "JDBC URL connection string"},
                          "username": {"type": "string", "description": "Database user name"},
                          "password": {"type": "string", "description": "Database user password"},
                          "sql": {"type": "string", "description": "SQL to execute"}
                        },
                        "requiredProperties": ["jdbcUrl", "username", "password", "sql"],
                        "arguments": {
                          "result-file": "Path to the file to store any results",
                          "verbose": "Log more info"
                        },
                        "requiredArguments": ["result-file"]
                      }
                    },
                    "execute_sql_file": {
                      "description": "Use for SQL execution on a database when you have the SQL as file",
                      "executable": "com.plugin.DatabaseExecuteSQLFileTool",  //optional, must only be specified if jar contains multiple main-classes
                      "inputSchema": {
                        "type": "object",
                        "properties": {
                          "jdbcUrl": {"type": "string", "description": "JDBC URL connection string"},
                          "username": {"type": "string", "description": "Database user name"},
                          "password": {"type": "string", "description": "Database user password"},
                          "file": {"type": "string", "description": "Path to SQL file to execute"}
                        },
                        "requiredProperties": ["jdbcUrl", "username", "password", "sql"],
                        "arguments": {
                          "result-file": "Path to the file to store any results",
                          "verbose": "Log more info"
                        },
                        "requiredArguments": ["result-file"]
                      }
                    }
                  }
                }
                ```

                ### 3. Javascript Plugin
                Use the standard npm project folder structure:

                my-plugin/
                ├─ package.json
                └─ src/plugin.js

                **Create the project**:
                npm init -y
                mkdir src

                **npm source folder**: src/
                **Build or prepare**: npm run build
                **Build output**: dist/ or build/
                **Set environment properties**:
                    * Linux/MacOs: export <property-key=property-value>
                    * Windows: set <property-key=property-value>
                **Run tool**: node <executable> <argument1> <argument2>

                Example configuration 'plugin.json' structure:
                ```json
                {
                  "name": "database-plugin",
                  "enabled": true,
                  "type": "SCRIPT",
                  "tools": {
                    "execute_sql": {
                      "description": "Use for SQL execution on a database when you have the SQL as text",
                      "executable": "build/execute_sql.js",
                      "inputSchema": {
                        "type": "object",
                        "properties": {
                          "jdbcUrl": {"type": "string", "description": "JDBC URL connection string"},
                          "username": {"type": "string", "description": "Database user name"},
                          "password": {"type": "string", "description": "Database user password"},
                          "sql": {"type": "string", "description": "SQL to execute"}
                        },
                        "requiredProperties": ["jdbcUrl", "username", "password", "sql"],
                        "arguments": {
                          "result-file": "Path to the file to store any results",
                          "verbose": "Log more info"
                        },
                        "requiredArguments": ["result-file"]
                      }
                    },
                    "execute_sql_file": {
                      "description": "Use for SQL execution on a database when you have the SQL as file",
                      "executable": "build/execute_sql_file.js",
                      "inputSchema": {
                        "type": "object",
                        "properties": {
                          "jdbcUrl": {"type": "string", "description": "JDBC URL connection string"},
                          "username": {"type": "string", "description": "Database user name"},
                          "password": {"type": "string", "description": "Database user password"},
                          "file": {"type": "string", "description": "Path to SQL file to execute"}
                        },
                        "requiredProperties": ["jdbcUrl", "username", "password", "sql"],
                        "arguments": {
                          "result-file": "Path to the file to store any results",
                          "verbose": "Log more info"
                        },
                        "requiredArguments": ["result-file"]
                      }
                    }
                  }
                ```

                ### 4. Python Plugin
                Use the standard Python project folder structure:

                my-plugin/
                ├─ src/
                │  └─ plugin.py
                └─ requirements.txt


                **Install dependencies**: pip install -r requirements.txt
                **Set environment properties**:
                    * Linux/MacOs: export <property-key=property-value>
                    * Windows: set <property-key=property-value>
                **Run a tool**: python <executable> <argument1> <argument2>

                Example configuration 'plugin.json' structure:
                ```json
                {
                  "name": "database-plugin",
                  "enabled": true,
                  "type": "SCRIPT",
                  "tools": {
                    "execute_sql": {
                      "description": "Use for SQL execution on a database when you have the SQL as text",
                      "executable": "src/execute_sql.py",
                      "inputSchema": {
                        "type": "object",
                        "properties": {
                          "jdbcUrl": {"type": "string", "description": "JDBC URL connection string"},
                          "username": {"type": "string", "description": "Database user name"},
                          "password": {"type": "string", "description": "Database user password"},
                          "sql": {"type": "string", "description": "SQL to execute"}
                        },
                        "requiredProperties": ["jdbcUrl", "username", "password", "sql"],
                        "arguments": {
                          "result-file": "Path to the file to store any results",
                          "verbose": "Log more info"
                        },
                        "requiredArguments": ["result-file"]
                      }
                    },
                    "execute_sql_file": {
                      "description": "Use for SQL execution on a database when you have the SQL as file",
                      "executable": "src/execute_sql_file.py",
                      "inputSchema": {
                        "type": "object",
                        "properties": {
                          "jdbcUrl": {"type": "string", "description": "JDBC URL connection string"},
                          "username": {"type": "string", "description": "Database user name"},
                          "password": {"type": "string", "description": "Database user password"},
                          "file": {"type": "string", "description": "Path to SQL file to execute"}
                        },
                        "requiredProperties": ["jdbcUrl", "username", "password", "sql"],
                        "arguments": {
                          "result-file": "Path to the file to store any results",
                          "verbose": "Log more info"
                        },
                        "requiredArguments": ["result-file"]
                      }
                    }
                  }
                ```

                ### 4. Bash shell script Plugin
                Use the following folder structure:

                my-plugin/
                └─ plugin.sh

                **Set environment properties**:
                    * Linux/MacOs: export <property-key=property-value>
                    * Windows: set <property-key=property-value>
                **Run a tool**: bash <executable> <argument1> <argument2>

                Example configuration 'plugin.json' structure:
                ```json
                {
                  "name": "database-plugin",
                  "enabled": true,
                  "type": "SCRIPT",
                  "tools": {
                    "execute_sql": {
                      "description": "Use for SQL execution on a database when you have the SQL as text",
                      "executable": "src/execute_sql.sh",
                      "inputSchema": {
                        "type": "object",
                        "properties": {
                          "jdbcUrl": {"type": "string", "description": "JDBC URL connection string"},
                          "username": {"type": "string", "description": "Database user name"},
                          "password": {"type": "string", "description": "Database user password"},
                          "sql": {"type": "string", "description": "SQL to execute"}
                        },
                        "requiredProperties": ["jdbcUrl", "username", "password", "sql"],
                        "arguments": {
                          "result-file": "Path to the file to store any results",
                          "verbose": "Log more info"
                        },
                        "requiredArguments": ["result-file"]
                      }
                    },
                    "execute_sql_file": {
                      "description": "Use for SQL execution on a database when you have the SQL as file",
                      "executable": "src/execute_sql_file.sh",
                      "inputSchema": {
                        "type": "object",
                        "properties": {
                          "jdbcUrl": {"type": "string", "description": "JDBC URL connection string"},
                          "username": {"type": "string", "description": "Database user name"},
                          "password": {"type": "string", "description": "Database user password"},
                          "file": {"type": "string", "description": "Path to SQL file to execute"}
                        },
                        "requiredProperties": ["jdbcUrl", "username", "password", "sql"],
                        "arguments": {
                          "result-file": "Path to the file to store any results",
                          "verbose": "Log more info"
                        },
                        "requiredArguments": ["result-file"]
                      }
                    }
                  }
                ```

                ### 5. HTTP_API Tool
                Use the following folder structure:

                my-plugin/
                └─ plugin.sh

                **Set environment properties**:
                    * Linux/MacOs: export <property-key=property-value>
                    * Windows: set <property-key=property-value>
                Reference the environment property with either:
                    * Linux/MacOs: $<property-key>
                    * Windows: %<property-key>%
                **Run tool**: curl -o <responseFile> -H <header-key1: header-value1> -H <header-key2: header-value2> -X <method> <url> -d <body>

                Example configuration 'plugin.json' structure:
                ```json
                {
                  "name": "database-plugin",
                  "enabled": true,
                  "type": "HTTP_API",
                  "tools": {
                    "execute_sql": {
                      "description": "Use for SQL execution on a database via an API endpoint",
                      "url": "https://api.dbaccess.com/v1/current",
                      "method": "POST",
                      "inputSchema": {
                        "type": "object",
                        "properties": {
                          "DB_USER": {"type": "string", "description": "Database user name"},
                          "DB_PASSWORD": {"type": "string", "description": "Database user password"},
                          "API-KEY": {"type": "string", "description": "API-Key/Access-token to access the web API"},
                        },
                        "requiredProperties": ["username", "password"],
                        "parameters": {
                          "jdbcUrl": {"type": "string", "description": "JDBC URL connection string"},
                          "username": {"type": "string", "description": "Database user"}
                        },
                        "requiredParameters": ["jdbcUrl"],
                        "headers": {
                          "Authorization": "Bearer $API_KEY",
                          "Content-Type": "Content-type of body",
                          "Content-Length": "Content-length of body"
                        },
                        "requiredHeaders": ["Authorization"],
                        "body": ["Content of body that complies with Content-Type"],
                        "responseFile": "Path to the file to store the response" // this is optional and only for when a response is returned
                      }
                    }
                  }
                ```

                ## Skills

                ### Basic Skill
                ```json
                {
                  "name": "code-reviewer",
                  "description": "Expert code review",
                  "enabled": true,
                  "systemPrompt": "You are an expert code reviewer. Focus on: security, performance, best practices, readability.",
                  "temperature": 0.3,
                  "examples": [
                    {
                      "input": "Review this function...",
                      "output": "Issues found: 1. Security..."
                    }
                  ]
                }
                ```

                ### Advanced Skill with Tools
                ```json
                {
                  "name": "test-generator",
                  "description": "Generate unit tests",
                  "enabled": true,
                  "systemPrompt": "Generate comprehensive unit tests with edge cases.",
                  "temperature": 0.5,
                  "requiredTools": ["read_file", "write_file", "bash"],
                  "examples": [
                    {
                      "input": "Generate tests for UserService.java",
                      "output": "Creating test file with 15 test cases..."
                    }
                  ],
                  "metadata": {
                    "framework": "JUnit5",
                    "coverage_target": "80%"
                  }
                }
                ```

                **File**: `~/.t1-super-ai/skills/skill_name.json`

                ## MCP Servers

                ### STDIO Server
                ```json
                {
                  "name": "local-tools",
                  "enabled": true,
                  "transport": "STDIO",
                  "command": "node",
                  "args": ["/path/to/server.js"],
                  "env": {
                    "API_KEY": "value"
                  }
                }
                ```

                ### HTTP Server
                ```json
                {
                  "name": "remote-api",
                  "enabled": true,
                  "transport": "HTTP",
                  "url": "http://localhost:8080/mcp",
                  "headers": {
                    "Authorization": "Bearer token"
                  }
                }
                ```

                ### SSE Server
                ```json
                {
                  "name": "streaming-service",
                  "enabled": true,
                  "transport": "SSE",
                  "url": "http://localhost:9000/events",
                  "reconnectInterval": 5000
                }
                ```

                **File**: `~/.t1-super-ai/mcp-servers/server_name.json`

                ## Notes

                - Changes to JSON files are hot-reloaded automatically
                - Tool/plugin names must be unique
                - Use lowercase with underscores for names: `my_tool_name`
                - Test tools with: CLI command or direct execution
                - Check logs for errors: Look for ERROR or WARN messages
                - Validate JSON: Use `cat file.json | jq .` to check syntax

                **End of Usage Guide**
                """;
    }

    /**
     * Read USAGE.md content for an agent
     */
    public String readUsageMd(final String agentName) throws IOException {
        final Path usageFile = getUsageMdPath(agentName);
        if (!Files.exists(usageFile)) {
            return null;
        }
        return Files.readString(usageFile, StandardCharsets.UTF_8);
    }

    /**
     * Check if an agent configuration exists (checks for CHARACTER.md)
     */
    public boolean agentConfigExists(final String agentName) {
        final Path characterFile = getCharacterMdPath(agentName);
        return Files.exists(characterFile);
    }

    /**
     * Update provider and model in CHARACTER.md
     */
    public void updateProviderAndModel(final String agentName, final String provider, final String model) throws IOException {
        final IndividualAgentConfig config = loadIndividualAgentConfig(agentName);
        config.setProvider(LLMProvider.valueOf(model));
        config.setModel(model);
        updateCharacterMd(config);
        log.info("✅ Updated provider={} and model={} in CHARACTER.md for agent '{}'", provider, model, agentName);
    }

    /**
     * List all agent configurations (supports both folder and legacy formats)
     */
    public List<IndividualAgentConfig> listAllAgentConfigs() {
        final List<IndividualAgentConfig> configs = new ArrayList<>();
        final File agentsDir = AGENTS_DIR.toFile();

        log.info("📂 Scanning for agent configs in: {}", agentsDir.getAbsolutePath());

        if (!agentsDir.exists() || !agentsDir.isDirectory()) {
            log.warn("⚠️ Agents directory does not exist: {}", agentsDir.getAbsolutePath());
            return configs;
        }

        // Check for new folder-based structure (read from CHARACTER.md instead of config.json)
        final File[] folders = agentsDir.listFiles(File::isDirectory);
        if (folders != null) {
            log.info("Found {} agent folders", folders.length);
            for (final File folder : folders) {
                final File characterFile = new File(folder, "CHARACTER.md");
                final String agentName = folder.getName();

                log.debug("Checking for CHARACTER.md in: {}/CHARACTER.md", agentName);
                if (characterFile.exists()) {
                    try {
                        final IndividualAgentConfig config = loadIndividualAgentConfig(agentName);
                        configs.add(config);
                        log.info("✅ Loaded agent config from CHARACTER.md: {}", config.getName());
                    } catch (final IOException e) {
                        log.error("❌ Error loading agent config from CHARACTER.md for: {}", agentName, e);
                    }
                } else {
                    log.warn("⚠️ No CHARACTER.md found in folder: {}", agentName);
                }
            }
        } else {
            log.warn("⚠️ No agent folders found in: {}", agentsDir.getAbsolutePath());
        }

        // Also check for legacy format (JSON files in root)
        final File[] files = agentsDir.listFiles((dir, name) -> name.endsWith(".json"));
        if (files != null && files.length > 0) {
            log.info("Found {} legacy config files", files.length);
            for (final File file : files) {
                try {
                    final IndividualAgentConfig config = objectMapper.readValue(file, IndividualAgentConfig.class);
                    configs.add(config);
                    log.info("✅ Loaded legacy agent config: {}", config.getName());
                } catch (final IOException e) {
                    log.error("❌ Error loading agent config from file: {}", file.getName(), e);
                }
            }
        }

        log.info("📋 Total agent configs loaded: {}", configs.size());
        return configs;
    }

    /**
     * Delete an agent configuration and its folder
     */
    public boolean deleteAgentConfig(final String agentName) {
        final Path agentFolder = getAgentFolder(agentName);

        try {
            if (Files.exists(agentFolder)) {
                // Delete all files in the folder
                Files.walk(agentFolder)
                .sorted((p1, p2) -> -p1.compareTo(p2)) // Reverse order to delete files before folders
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (final IOException e) {
                        log.error("Error deleting file: {}", path, e);
                    }
                });
                log.info("Deleted agent folder: {}", agentName);
                return true;
            }

            // Also check legacy format
            final Path legacyFile = AGENTS_DIR.resolve(agentName + ".json");
            if (Files.exists(legacyFile)) {
                final boolean deleted = Files.deleteIfExists(legacyFile);
                if (deleted) {
                    log.info("Deleted legacy agent configuration: {}", agentName);
                }
                return deleted;
            }

        } catch (final IOException e) {
            log.error("Error deleting agent configuration: {}", agentName, e);
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

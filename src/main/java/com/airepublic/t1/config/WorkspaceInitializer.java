package com.airepublic.t1.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Initializes the workspace directory with instructional files on first run.
 * Copies template files from resources to ~/.t1-super-ai/
 */
@Slf4j
@Component
public class WorkspaceInitializer {
    // Use Paths.get() for cross-platform compatibility (Windows, Linux, macOS)
    // Default team name when none is specified
    private static final String DEFAULT_TEAM_NAME = "Default";

    // Root directory for shared resources and documentation
    private String rootDir;
    // Team-based workspace path: ~/.t1-super-ai/workspaces/<team_name>
    private String workspaceDir;
    private String teamName = DEFAULT_TEAM_NAME;

    public WorkspaceInitializer() {
        // Initialize root directory
        this.rootDir = Paths.get(System.getProperty("user.home"), ".t1-super-ai").toString();
        // Load the last active team (or default to "Default")
        loadCurrentTeam();
    }

    /**
     * Set the team name and configure workspace path accordingly
     * Must be called before initializeWorkspace() to configure team-based paths
     */
    public void setTeamName(final String teamName) {
        if (teamName != null && !teamName.trim().isEmpty()) {
            this.teamName = teamName;
        } else {
            // Use "Default" as team name if none specified
            this.teamName = DEFAULT_TEAM_NAME;
        }
        // Root directory stays the same
        this.rootDir = Paths.get(System.getProperty("user.home"), ".t1-super-ai").toString();
        // Team-based workspace: ~/..t1-super-ai/workspaces/<team_name>
        this.workspaceDir = Paths.get(System.getProperty("user.home"), ".t1-super-ai", "workspaces", this.teamName).toString();
        log.info("Configured team-based workspace for team '{}': {}", this.teamName, this.workspaceDir);

        // Persist current team to file
        saveCurrentTeam();
    }

    /**
     * Load the last active team from file
     */
    public void loadCurrentTeam() {
        try {
            final Path currentTeamFile = Paths.get(System.getProperty("user.home"), ".t1-super-ai", ".current-team");
            if (Files.exists(currentTeamFile)) {
                final String savedTeam = Files.readString(currentTeamFile).trim();
                if (!savedTeam.isEmpty()) {
                    setTeamName(savedTeam);
                    log.info("Loaded last active team: {}", savedTeam);
                    return;
                }
            }
        } catch (final IOException e) {
            log.warn("Could not load current team from file, using default", e);
        }
        // If no saved team or error, use default
        setTeamName(DEFAULT_TEAM_NAME);
    }

    /**
     * Save the current team to file
     */
    private void saveCurrentTeam() {
        try {
            final Path currentTeamFile = Paths.get(System.getProperty("user.home"), ".t1-super-ai", ".current-team");
            Files.createDirectories(currentTeamFile.getParent());
            Files.writeString(currentTeamFile, this.teamName);
            log.debug("Saved current team to file: {}", this.teamName);
        } catch (final IOException e) {
            log.warn("Could not save current team to file", e);
        }
    }

    /**
     * Get the current workspace directory path
     */
    public String getWorkspaceDir() {
        return workspaceDir;
    }

    /**
     * Get the current team name (null if using default workspace)
     */
    public String getTeamName() {
        return teamName;
    }

    /**
     * Initialize workspace with all required documentation files
     * Returns true if this is the first run (HATCH.md created)
     */
    public boolean initializeWorkspace() {
        try {
            final Path workspacePath = Paths.get(workspaceDir);

            // Create team workspace directories
            Files.createDirectories(workspacePath);
            Files.createDirectories(Paths.get(workspaceDir, "agents"));

            // Create shared directories in ~/.t1-super-ai (not team-specific)
            final Path rootWorkspace = Paths.get(System.getProperty("user.home"), ".t1-super-ai");
            Files.createDirectories(Paths.get(rootWorkspace.toString(), "tools"));
            Files.createDirectories(Paths.get(rootWorkspace.toString(), "plugins"));
            Files.createDirectories(Paths.get(rootWorkspace.toString(), "mcp-servers"));

            // Check if USER.md exists in root - if not, this is first run
            final Path userPath = Paths.get(rootDir, "USER.md");
            final boolean needsHatching = !Files.exists(userPath);

            if (needsHatching) {
                log.info("🥚 First run detected - creating instructional files");
                createInstructionalFiles();
                return true; // First run, needs HATCH
            } else {
                log.info("✅ Workspace initialized - USER.md exists");
                // Ensure other docs exist
                ensureDocumentationFiles();
                return false; // Already hatched
            }

        } catch (final IOException e) {
            log.error("Error initializing workspace", e);
            return false;
        }
    }

    /**
     * Create all instructional markdown files in the root directory
     */
    private void createInstructionalFiles() throws IOException {
        createHatchMd();
        createUsageMd();
        createReadmeMd();
        createQuickReferenceMd();
        createConfigurationGuideMd();

        log.info("📚 Created instructional files in {}", rootDir);
        log.info("🥚 Please review HATCH.md in {} to configure your agent", rootDir);
    }

    /**
     * Ensure documentation files exist in root directory (for updates/repairs)
     */
    private void ensureDocumentationFiles() throws IOException {
        if (!Files.exists(Paths.get(rootDir, "USAGE.md"))) {
            createUsageMd();
        }
        if (!Files.exists(Paths.get(rootDir, "README.md"))) {
            createReadmeMd();
        }
        if (!Files.exists(Paths.get(rootDir, "QUICK_REFERENCE.md"))) {
            createQuickReferenceMd();
        }
    }

    /**
     * Create HATCH.md - Initial setup wizard
     */
    private void createHatchMd() throws IOException {
        final Path hatchPath = Paths.get(rootDir, "HATCH.md");

        // Don't overwrite if it exists
        if (Files.exists(hatchPath)) {
            log.debug("HATCH.md already exists, skipping creation");
            return;
        }

        final String content = getHatchContent();
        Files.writeString(hatchPath, content);
        log.info("✨ Created HATCH.md in {}", rootDir);
    }

    /**
     * Create USAGE.md - Comprehensive usage guide
     */
    private void createUsageMd() throws IOException {
        final Path targetFile = Paths.get(rootDir, "USAGE.md");

        // Don't overwrite if it exists
        if (Files.exists(targetFile)) {
            log.debug("USAGE.md already exists, skipping creation");
            return;
        }

        // Create comprehensive usage guide with examples
        final String content = getUsageContent();
        Files.writeString(targetFile, content);
        log.info("Created USAGE.md in {}", rootDir);
    }

    private String getUsageContent() {
        return """
                # T1 Super AI - Usage Guide

                **Location**: `~/.t1-super-ai/`

                ## Workspace Structure

                T1 Super AI uses a team-based workspace structure:
                - Root directory: `~/.t1-super-ai/` - Contains shared resources and documentation
                - Team workspaces: `~/.t1-super-ai/workspaces/{team-name}/` - Contains team-specific agents
                - Shared resources:
                  - `~/.t1-super-ai/tools/` - Shared tools
                  - `~/.t1-super-ai/plugins/` - Shared plugins
                  - `~/.t1-super-ai/mcp-servers/` - MCP server configurations
                  - `~/.t1-super-ai/skills/` - Shared skills

                ## Plugins (5 types)
                Plugins are a collection of one or more tools.
                Plugins are stored in the shared plugins folder: `~/.t1-super-ai/plugins/<plugin-name>`.
                """ + """
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
     * Create README.md
     */
    private void createReadmeMd() throws IOException {
        final Path targetFile = Paths.get(rootDir, "README.md");

        // Don't overwrite if it exists
        if (Files.exists(targetFile)) {
            log.debug("README.md already exists, skipping creation");
            return;
        }

        final String content = "# T1 Super AI - User Configuration\n\n" +
                "**Root Directory:** `~/.t1-super-ai/` (in your user home directory)\n\n" +
                "**Shared Resources:** Tools, plugins, and MCP servers accessible to all teams\n\n" +
                "## Directory Structure\n\n" +
                "```\n" +
                "~/.t1-super-ai/\n" +
                "├── HATCH.md                # Initial setup guide\n" +
                "├── USER.md                 # User profile and preferences\n" +
                "├── USAGE.md                # Comprehensive usage guide\n" +
                "├── README.md               # This file\n" +
                "├── QUICK_REFERENCE.md      # Quick command reference\n" +
                "├── CONFIGURATION_GUIDE.md  # Configuration examples\n" +
                "├── tools/                  # Shared tool configurations\n" +
                "├── plugins/                # Shared plugin configurations\n" +
                "├── mcp-servers/            # Shared MCP server configurations\n" +
                "├── skills/                 # Shared skill configurations\n" +
                "└── workspaces/             # Team-based workspaces\n" +
                "    ├── Default/            # Default team workspace\n" +
                "    │   └── agents/         # Team-specific agents\n" +
                "    │       └── {agent}/\n" +
                "    │           ├── CHARACTER.md    # Agent personality profile\n" +
                "    │           ├── USAGE.md        # Agent-specific usage guide\n" +
                "    │           ├── skills/         # Agent-specific skills\n" +
                "    │           ├── Content/        # Agent task workspace\n" +
                "    │           └── Downloads/      # Agent downloads\n" +
                "    └── {other-teams}/      # Additional team workspaces\n" +
                "```\n\n" +
                "## Documentation Files\n\n" +
                "- **HATCH.md** - Initial setup guide (delete after setup)\n" +
                "- **USER.md** - Your user profile and preferences  \n" +
                "- **USAGE.md** - Comprehensive usage guide for plugins, tools, and MCP servers\n" +
                "- **QUICK_REFERENCE.md** - Quick command reference\n" +
                "- **CONFIGURATION_GUIDE.md** - Configuration examples\n" +
                "- **workspaces/{team}/agents/{name}/CHARACTER.md** - Agent-specific personality profiles\n" +
                "- **workspaces/{team}/agents/{name}/USAGE.md** - Agent-specific usage instructions\n\n" +
                "## Getting Started\n\n" +
                "1. Complete the HATCH process if you haven't already\n" +
                "2. Switch teams using the team selector in the UI\n" +
                "3. Create agents within each team workspace\n" +
                "4. Configure shared plugins, tools, and MCP servers for use across all teams\n\n" +
                "See individual documentation files for detailed information.\n";
        Files.writeString(targetFile, content);
        log.info("Created README.md in {}", rootDir);
    }

    /**
     * Create QUICK_REFERENCE.md
     */
    private void createQuickReferenceMd() throws IOException {
        final Path targetFile = Paths.get(rootDir, "QUICK_REFERENCE.md");

        // Don't overwrite if it exists
        if (Files.exists(targetFile)) {
            log.debug("QUICK_REFERENCE.md already exists, skipping creation");
            return;
        }

        final String content = "# T1 Super AI - Quick Reference\n\n" +
                "## Configuration Locations\n\n" +
                "**Root directory:** `~/.t1-super-ai/`\n" +
                "**Shared resources:** `~/.t1-super-ai/{tools,plugins,mcp-servers,skills}/`\n" +
                "**Team workspaces:** `~/.t1-super-ai/workspaces/{team-name}/`\n\n" +
                "## Common Commands\n\n" +
                "```bash\n" +
                "# List shared plugins\n" +
                "ls ~/.t1-super-ai/plugins/\n\n" +
                "# View a plugin\n" +
                "cat ~/.t1-super-ai/plugins/my-plugin/plugin.json\n\n" +
                "# List shared tools\n" +
                "ls ~/.t1-super-ai/tools/\n\n" +
                "# List MCP servers\n" +
                "ls ~/.t1-super-ai/mcp-servers/\n\n" +
                "# List teams\n" +
                "ls ~/.t1-super-ai/workspaces/\n\n" +
                "# List agents in a team\n" +
                "ls ~/.t1-super-ai/workspaces/Default/agents/\n\n" +
                "# View agent character\n" +
                "cat ~/.t1-super-ai/workspaces/Default/agents/master/CHARACTER.md\n" +
                "```\n\n" +
                "## Plugin/Tool Creation\n\n" +
                "See USAGE.md for detailed instructions on creating:\n" +
                "- Java (JAR/Maven) plugins\n" +
                "- Python plugins\n" +
                "- JavaScript plugins\n" +
                "- Bash script plugins\n" +
                "- HTTP API tools\n" +
                "- MCP servers\n" +
                "- Skills\n\n" +
                "See CONFIGURATION_GUIDE.md for configuration examples.\n";
        Files.writeString(targetFile, content);
        log.info("Created QUICK_REFERENCE.md in {}", rootDir);
    }

    /**
     * Create CONFIGURATION_GUIDE.md
     */
    private void createConfigurationGuideMd() throws IOException {
        final Path targetFile = Paths.get(rootDir, "CONFIGURATION_GUIDE.md");

        // Don't overwrite if it exists
        if (Files.exists(targetFile)) {
            log.debug("CONFIGURATION_GUIDE.md already exists, skipping creation");
            return;
        }

        final String content = "# Dynamic Configuration System - Quick Start Guide\n\n" +
                "The T1 Super AI supports dynamic loading of plugins, tools, MCP servers, and skills through JSON configuration files " +
                "stored in `~/.t1-super-ai/` (your user home directory).\n\n" +
                "## Quick Start\n\n" +
                "### 1. View Example Plugins\n\n" +
                "```bash\n" +
                "ls ~/.t1-super-ai/plugins/\n" +
                "cat ~/.t1-super-ai/plugins/example-plugin/plugin.json\n" +
                "```\n\n" +
                "### 2. Create Your First Plugin\n\n" +
                "```bash\n" +
                "mkdir -p ~/.t1-super-ai/plugins/my-plugin\n" +
                "cat > ~/.t1-super-ai/plugins/my-plugin/plugin.json <<'EOF'\n" +
                "{\n" +
                "  \"name\": \"my_tool\",\n" +
                "  \"description\": \"My custom tool\",\n" +
                "  \"enabled\": true,\n" +
                "  \"type\": \"SCRIPT\",\n" +
                "  \"executionScript\": \"echo 'Hello from my tool!'\",\n" +
                "  \"scriptLanguage\": \"bash\"\n" +
                "}\n" +
                "EOF\n" +
                "```\n\n" +
                "The tool is now immediately available (hot-reload)!\n\n" +
                "See USAGE.md for complete documentation.\n";
        Files.writeString(targetFile, content);
        log.info("Created CONFIGURATION_GUIDE.md in {}", rootDir);
    }

    /**
     * Read USER.md for session context
     */
    public String readUserMd() {
        try {
            final Path userPath = Paths.get(rootDir, "USER.md");
            if (Files.exists(userPath)) {
                return Files.readString(userPath);
            }
        } catch (final IOException e) {
            log.error("Error reading USER.md", e);
        }
        return null;
    }

    /**
     * Read USAGE.md for session context
     */
    public String readUsageMd() {
        try {
            final Path usagePath = Paths.get(rootDir, "USAGE.md");
            if (Files.exists(usagePath)) {
                return Files.readString(usagePath);
            }
        } catch (final IOException e) {
            log.error("Error reading USAGE.md", e);
        }
        return null;
    }

    /**
     * Read HATCH.md for initial setup
     */
    public String readHatchMd() {
        try {
            final Path hatchPath = Paths.get(rootDir, "HATCH.md");
            if (Files.exists(hatchPath)) {
                return Files.readString(hatchPath);
            }
        } catch (final IOException e) {
            log.error("Error reading HATCH.md", e);
        }
        return null;
    }

    /**
     * Check if hatching is needed (USER.md doesn't exist)
     */
    public boolean needsHatching() {
        return !Files.exists(Paths.get(rootDir, "USER.md"));
    }

    // Template content methods (these would normally read from resources)

    private String getHatchContent() {
        return """
                # 🥚 Welcome to T1 Super AI - Let's Hatch Your Agent!

                This appears to be your first time running T1 Super AI. Let's set up your personalized AI assistant!

                I'll ask you a few questions to understand:
                - Who you are and what you work on
                - What you'd like to call me (your agent)
                - How I should communicate with you
                - What tools and capabilities you need

                This will only take a few minutes, and you can always adjust these settings later by editing agent files in `~/.t1-super-ai/agents/{agent-name}/`.

                ---

                ## Step 1: Tell Me About You

                **Your Name:**
                > What should I call you?
                >
                > Example: "Alex", "Dr. Smith", "Team Lead", etc.

                **Your Preferred Pronouns (optional):**
                > How should I refer to you?
                >
                > Example: "they/them", "she/her", "he/him", etc.

                **Your Primary Work Focus:**
                > What do you mainly work on?
                >
                > Example: "Full-stack web development", "Data science", "DevOps", "Research", etc.

                ---

                ## Step 2: Team Configuration

                **Team Name (optional):**
                > What team are you part of? Leave blank to use the default workspace.
                >
                > Example: "DevOps", "DataScience", "Frontend", etc.
                >
                > Note: If you specify a team name, your workspace will be located at:
                > ~/.t1-super-ai/workspaces/<team_name>/
                >
                > Otherwise, the default location will be used:
                > ~/.t1-super-ai/

                ---

                ## Step 3: Name Your Agent

                **Agent Name:**
                > What would you like to call me (your AI agent)?
                >
                > Example: "Atlas", "CodeWizard", "DevBuddy", "Professor", "Sage", etc.
                >
                > Note: This is just a friendly name - you can always change it later!

                **My Primary Purpose:**
                > What is the main reason you're using this agent?
                >
                > Examples:
                > - "Help with daily coding tasks and debugging"
                > - "Research assistant for scientific papers"
                > - "DevOps automation and monitoring"
                > - "Learning and teaching programming concepts"
                > - "Project management and documentation"

                ---

                ## Step 4: Set Communication Style

                **How should I communicate with you?**

                Choose a style or describe your preference:
                - **Professional** - Formal, precise, business-like
                - **Friendly** - Casual, warm, conversational
                - **Concise** - Brief, to-the-point, minimal explanation
                - **Educational** - Detailed explanations, teaching-focused
                - **Enthusiastic** - Energetic, encouraging, positive

                **Should I use emojis?**
                - **Yes, freely** - Use emojis to add personality
                - **Sparingly** - Only when it adds clarity
                - **No emojis** - Text only, professional

                ---

                ## Ready to Begin?

                Once you start chatting with me, I'll guide you through these questions naturally.
                After we're done, I'll create your agent profile with your preferences.

                **Let's get started!** 🚀

                ---

                _This file will be automatically deleted after your agent is configured._
                """ +
                String.format("_Location: ~/.t1-super-ai/workspaces/%s/HATCH.md_\n", teamName);
    }

    /**
     * Complete the hatching process by creating USER.md
     * This is called during the initial hatching process after collecting user information
     */
    public void createUserProfile(
            final String userName,
            final String userPronouns,
            final String userFocus,
            final String defaultAgent) throws IOException {

        // Create USER.md with user information
        createUserMd(userName, userPronouns, userFocus, defaultAgent);

        // Note: The initial agent CHARACTER.md will be created by the calling code
        // using AgentConfigService.createCharacterMd() for the agent folder

        // Delete HATCH.md to mark setup complete
        final Path hatchPath = Paths.get(rootDir, "HATCH.md");
        Files.deleteIfExists(hatchPath);
        log.info("🎉 Setup complete - HATCH.md removed from root directory");
    }

    /**
     * Create the USER.md file in root directory with user information and team name
     * This is called during the initial hatching process
     */
    public void createUserMd(
            final String userName,
            final String userPronouns,
            final String userFocus,
            final String defaultAgent) throws IOException {

        final Path userPath = Paths.get(rootDir, "USER.md");
        final String template = AgentConfigService.getUserTemplateContent();

        final String teamInfo = String.format("\n**Team:** %s\n", teamName);

        final String content = template
                .replace("{{TIMESTAMP}}", java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .replace("{{USER_NAME}}", userName)
                .replace("{{USER_PRONOUNS}}", userPronouns)
                .replace("{{USER_FOCUS}}", userFocus)
                .replace("{{USER_TIMEZONE}}", System.getProperty("user.timezone", "UTC"))
                .replace("{{DEFAULT_AGENT}}", defaultAgent != null ? defaultAgent : "")
                .replace("## Active Agent", teamInfo + "## Active Agent");

        Files.writeString(userPath, content);
        log.info("✅ Created USER.md in root directory ({}) with default agent: {} (team: {})", rootDir, defaultAgent, teamName);
    }
}

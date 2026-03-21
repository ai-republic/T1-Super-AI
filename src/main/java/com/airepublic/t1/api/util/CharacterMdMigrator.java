package com.airepublic.t1.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;

/**
 * Utility to migrate existing CHARACTER.md files to use properly quoted paths.
 * This fixes the issue where paths with spaces (like "General Assistants") would fail
 * when used in shell commands without proper quoting.
 */
@Slf4j
public class CharacterMdMigrator {

    public static void main(String[] args) {
        Path workspacesRoot = Paths.get(System.getProperty("user.home"), ".t1-super-ai", "workspaces");

        if (!Files.exists(workspacesRoot)) {
            log.warn("No workspaces directory found at: {}", workspacesRoot);
            return;
        }

        log.info("Starting CHARACTER.md migration...");
        log.info("Scanning workspaces at: {}", workspacesRoot);

        try (Stream<Path> workspaces = Files.list(workspacesRoot)) {
            workspaces.filter(Files::isDirectory).forEach(teamWorkspace -> {
                String teamName = teamWorkspace.getFileName().toString();
                log.info("Processing team: {}", teamName);

                Path agentsDir = teamWorkspace.resolve("agents");
                if (!Files.exists(agentsDir)) {
                    log.info("  No agents directory found for team: {}", teamName);
                    return;
                }

                try (Stream<Path> agents = Files.list(agentsDir)) {
                    agents.filter(Files::isDirectory).forEach(agentDir -> {
                        String agentName = agentDir.getFileName().toString();
                        Path characterMd = agentDir.resolve("CHARACTER.md");

                        if (Files.exists(characterMd)) {
                            try {
                                migrateCharacterMd(characterMd, teamName, agentName);
                            } catch (IOException e) {
                                log.error("Failed to migrate CHARACTER.md for agent: {}/{}", teamName, agentName, e);
                            }
                        }
                    });
                } catch (IOException e) {
                    log.error("Failed to list agents for team: {}", teamName, e);
                }
            });
        } catch (IOException e) {
            log.error("Failed to list workspaces", e);
        }

        log.info("CHARACTER.md migration completed!");
    }

    private static void migrateCharacterMd(Path characterMdPath, String teamName, String agentName) throws IOException {
        log.info("  Migrating: {}/{}", teamName, agentName);

        String content = Files.readString(characterMdPath, StandardCharsets.UTF_8);

        // Check if already migrated to new format (with $HOME instead of ~)
        if (content.contains("$HOME/.t1-super-ai/workspaces/")) {
            log.info("    Already migrated to new format, skipping");
            return;
        }

        // Build the quoted paths
        String quotedUnixPath = PathUtils.buildQuotedUnixPath(teamName, agentName);
        String quotedWindowsPath = PathUtils.buildQuotedWindowsPath(teamName, agentName);
        String quotedUnixWorkspace = PathUtils.buildQuotedUnixPath(teamName, null);
        String quotedWindowsWorkspace = PathUtils.buildQuotedWindowsPath(teamName, null);

        // Find and replace the Environment section
        String[] lines = content.split("\n");
        StringBuilder newContent = new StringBuilder();
        boolean inEnvironmentSection = false;
        boolean replacedEnvironment = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            if (line.trim().equals("## 📋 Environment")) {
                inEnvironmentSection = true;
                newContent.append(line).append("\n");

                // Replace entire environment section with new version
                newContent.append("Your workspace root is in the user's home directory.\n\n");
                newContent.append("**IMPORTANT: Always use these properly quoted paths when executing shell commands:**\n");
                newContent.append(String.format("- Linux/macOS: %s\n", quotedUnixPath));
                newContent.append(String.format("- Windows: %s\n", quotedWindowsPath));
                newContent.append("\n");
                newContent.append("These paths are safe to use with bash, cmd, and all file operation tools because they handle spaces and special characters correctly.\n");
                newContent.append("\n");
                newContent.append("Use these subfolders:\n");
                newContent.append("- `Content` — for all files you create, generate, modify, or organize for the task\n");
                newContent.append("- `Downloads` — for all files downloaded from the internet\n");
                newContent.append("\n");
                newContent.append("Rules:\n");
                newContent.append(String.format("1. Your agent workspace is at: %s (Linux/macOS) or %s (Windows)\n", quotedUnixPath, quotedWindowsPath));
                newContent.append("2. Before starting, create a folder structure and files that represent the task.\n");
                newContent.append("3. Save all created/generated files under `Content`.\n");
                newContent.append("4. Save all internet-downloaded files under `Downloads`.\n");
                newContent.append("5. Do not write task files outside this workspace unless explicitly instructed or if you need to write a plugin, tool, skill, mcp-server or another agent (see USAGE.md).\n");
                newContent.append("6. Use clear, descriptive folder and file names.\n");
                newContent.append("7. **Always use the quoted path formats shown above when executing shell commands to avoid path resolution errors.**\n");
                newContent.append("\n");
                newContent.append("Example workspace structure:\n");
                // Build subfolder paths by adding escaped subfolder names
                String unixContentPath = quotedUnixPath.substring(0, quotedUnixPath.length() - 1) + "Content/";
                String winContentPath = quotedWindowsPath.substring(0, quotedWindowsPath.length() - 2) + "Content\"";
                String unixDownloadsPath = quotedUnixPath.substring(0, quotedUnixPath.length() - 1) + "Downloads/";
                String winDownloadsPath = quotedWindowsPath.substring(0, quotedWindowsPath.length() - 2) + "Downloads\"";

                newContent.append(String.format("- Linux/macOS Content folder: %s\n", unixContentPath));
                newContent.append(String.format("- Windows Content folder: %s\n", winContentPath));
                newContent.append(String.format("- Linux/macOS Downloads folder: %s\n", unixDownloadsPath));
                newContent.append(String.format("- Windows Downloads folder: %s\n", winDownloadsPath));
                newContent.append("\n");

                replacedEnvironment = true;

                // Skip old environment content until we hit the next section
                i++;
                while (i < lines.length && !lines[i].trim().startsWith("###") && !lines[i].trim().startsWith("##")) {
                    i++;
                }
                i--; // Back up one so we don't skip the next section header
                continue;
            }

            // Update MCP Servers section
            if (line.trim().equals("### MCP Servers")) {
                newContent.append(line).append("\n");
                // Read until we find a line that starts content
                i++;
                while (i < lines.length && lines[i].trim().isEmpty()) {
                    newContent.append(lines[i]).append("\n");
                    i++;
                }
                // Add the updated MCP servers text
                newContent.append("You are connected to a local MCP server that provides core tools that give you read/write access, bash and cmd to execute commands, web_search to search the web.\n");
                newContent.append(String.format("External MCP server configurations can be found under the workspace folder: %smcpservers' (Linux/macOS) or %smcpservers\" (Windows).\n",
                    quotedUnixWorkspace.substring(0, quotedUnixWorkspace.length() - 1),
                    quotedWindowsWorkspace.substring(0, quotedWindowsWorkspace.length() - 1)));

                // Skip old MCP content until next section
                while (i < lines.length && !lines[i].trim().startsWith("##") && !lines[i].trim().startsWith("---")) {
                    i++;
                }
                i--;
                continue;
            }

            // Update Skills section
            if (line.trim().equals("## Skills")) {
                newContent.append(line).append("\n");
                // Add updated skills text
                newContent.append(String.format("Custom skills can be found in the workspace folder: %sskills' (Linux/macOS) or %sskills\" (Windows).\n",
                    quotedUnixWorkspace.substring(0, quotedUnixWorkspace.length() - 1),
                    quotedWindowsWorkspace.substring(0, quotedWindowsWorkspace.length() - 1)));

                // Skip old skills content until next section
                i++;
                while (i < lines.length && !lines[i].trim().startsWith("###") && !lines[i].trim().startsWith("##") && !lines[i].trim().startsWith("---")) {
                    i++;
                }
                i--;
                continue;
            }

            // Update Plugins section
            if (line.trim().equals("### Plugins and tools")) {
                newContent.append(line).append("\n");
                // Add updated plugins text
                newContent.append(String.format("Custom plugins (each containing multiple tools) can be found in the workspace folder: %splugins' (Linux/macOS) or %splugins\" (Windows). Each tool has a 'plugin.json' file defining the tool and a README.md how to use each tool.\n",
                    quotedUnixWorkspace.substring(0, quotedUnixWorkspace.length() - 1),
                    quotedWindowsWorkspace.substring(0, quotedWindowsWorkspace.length() - 1)));

                // Skip old plugins content until next section
                i++;
                while (i < lines.length && !lines[i].trim().startsWith("---") && !lines[i].trim().startsWith("##")) {
                    i++;
                }
                i--;
                continue;
            }

            newContent.append(line).append("\n");
        }

        // Write the updated content
        Files.writeString(characterMdPath, newContent.toString(), StandardCharsets.UTF_8);
        log.info("    ✅ Migrated successfully");
    }
}

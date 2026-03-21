package com.airepublic.t1.util;

import java.util.regex.Pattern;

/**
 * Utility class for safe path handling with proper escaping of special characters.
 * Handles both Windows and Unix/Linux/macOS path formats.
 */
public class PathUtils {

    /**
     * Escape special regex characters in a string to use in regex replacement.
     * This is crucial for team names that may contain regex special characters.
     *
     * @param text The text to escape
     * @return The escaped text safe for use in regex
     */
    public static String escapeRegex(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        // Escape all special regex characters: . \ + * ? [ ^ ] $ ( ) { } = ! < > | : -
        return Pattern.quote(text);
    }

    /**
     * Replace team name in Unix/Linux/macOS path format.
     * Safely handles special characters in team names by escaping them for regex.
     * Also handles quoted paths for shell usage.
     *
     * @param content The content to update
     * @param oldTeamName The old team name to replace
     * @param newTeamName The new team name to replace with
     * @return Updated content with replaced paths
     */
    public static String replaceUnixPath(String content, String oldTeamName, String newTeamName) {
        if (content == null || oldTeamName == null || newTeamName == null) {
            return content;
        }

        // Replace unquoted paths (for display in markdown)
        String oldPathPattern = "~/.t1-super-ai/workspaces/" + escapeRegex(oldTeamName) + "/";
        String newPath = "~/.t1-super-ai/workspaces/" + newTeamName + "/";
        content = content.replaceAll(oldPathPattern, newPath);

        // Replace quoted paths (for shell command usage)
        // Build old quoted path: '~/.t1-super-ai/workspaces/Old Team Name/'
        String oldQuotedPath = buildQuotedUnixPath(oldTeamName, null);
        String newQuotedPath = buildQuotedUnixPath(newTeamName, null);
        // Also handle paths with agent placeholders
        String oldQuotedPathWithAgent = oldQuotedPath.replace("/'", "/{{NAME}}/'");
        String newQuotedPathWithAgent = newQuotedPath.replace("/'", "/{{NAME}}/'");

        content = content.replace(oldQuotedPath, newQuotedPath);
        content = content.replace(oldQuotedPathWithAgent, newQuotedPathWithAgent);

        return content;
    }

    /**
     * Replace team name in Windows path format.
     * Safely handles special characters in team names by escaping them for regex.
     * Also handles the double backslashes required in Windows paths and quoted paths.
     *
     * @param content The content to update
     * @param oldTeamName The old team name to replace
     * @param newTeamName The new team name to replace with
     * @return Updated content with replaced paths
     */
    public static String replaceWindowsPath(String content, String oldTeamName, String newTeamName) {
        if (content == null || oldTeamName == null || newTeamName == null) {
            return content;
        }

        // Replace unquoted paths (for display in markdown)
        // Escape backslashes for regex - Windows paths use double backslashes
        String oldPathPattern = "%USERPROFILE%\\\\.t1-super-ai\\\\workspaces\\\\" + escapeRegex(oldTeamName) + "\\\\";
        String newPath = "%USERPROFILE%\\\\.t1-super-ai\\\\workspaces\\\\" + escapeForBackslash(newTeamName) + "\\\\";
        content = content.replaceAll(oldPathPattern, newPath);

        // Replace quoted paths (for shell command usage)
        // Build old quoted path: "%USERPROFILE%\.t1-super-ai\workspaces\Old Team Name\"
        String oldQuotedPath = buildQuotedWindowsPath(oldTeamName, null);
        String newQuotedPath = buildQuotedWindowsPath(newTeamName, null);
        // Also handle paths with agent placeholders
        String oldQuotedPathWithAgent = oldQuotedPath.replace("\\\"", "\\{{NAME}}\\\"");
        String newQuotedPathWithAgent = newQuotedPath.replace("\\\"", "\\{{NAME}}\\\"");

        content = content.replace(oldQuotedPath, newQuotedPath);
        content = content.replace(oldQuotedPathWithAgent, newQuotedPathWithAgent);

        return content;
    }

    /**
     * Escape backslashes in the new team name for Windows paths.
     * If the new team name contains backslashes, they need to be doubled.
     *
     * @param teamName The team name to escape
     * @return The escaped team name
     */
    private static String escapeForBackslash(String teamName) {
        if (teamName == null) {
            return null;
        }
        // Replace single backslash with double backslash
        return teamName.replace("\\", "\\\\");
    }

    /**
     * Replace team name in both Unix and Windows path formats.
     * This is the primary method to use for updating file contents with new team names.
     *
     * @param content The content to update
     * @param oldTeamName The old team name to replace
     * @param newTeamName The new team name to replace with
     * @return Updated content with replaced paths in both formats
     */
    public static String replaceTeamPathsInContent(String content, String oldTeamName, String newTeamName) {
        if (content == null) {
            return null;
        }

        // Replace Unix/Linux/macOS paths
        content = replaceUnixPath(content, oldTeamName, newTeamName);

        // Replace Windows paths
        content = replaceWindowsPath(content, oldTeamName, newTeamName);

        return content;
    }

    /**
     * Validate team name for filesystem safety.
     * Checks if a team name is safe to use as a directory name.
     *
     * @param teamName The team name to validate
     * @return true if the team name is safe, false otherwise
     */
    public static boolean isValidTeamName(String teamName) {
        if (teamName == null || teamName.trim().isEmpty()) {
            return false;
        }

        // Disallow characters that are problematic in file systems:
        // Windows: < > : " / \ | ? *
        // Unix/Linux: / (null byte)
        // We'll be more restrictive for cross-platform compatibility
        String invalidChars = "[<>:\"/\\\\|?*\\x00]";
        return !teamName.matches(".*" + invalidChars + ".*");
    }

    /**
     * Sanitize team name by removing invalid characters.
     * Replaces invalid characters with underscores.
     *
     * @param teamName The team name to sanitize
     * @return Sanitized team name safe for filesystem use
     */
    public static String sanitizeTeamName(String teamName) {
        if (teamName == null) {
            return null;
        }

        // Replace invalid characters with underscores
        return teamName.replaceAll("[<>:\"/\\\\|?*\\x00]", "_");
    }

    /**
     * Quote a path for safe use in shell commands (Linux/macOS style).
     * Wraps the path in single quotes and escapes any single quotes within.
     *
     * @param path The path to quote
     * @return Quoted path safe for shell usage
     */
    public static String quoteUnixPath(String path) {
        if (path == null) {
            return null;
        }
        // Escape single quotes by replacing ' with '\''
        // Then wrap the whole path in single quotes
        return "'" + path.replace("'", "'\\''") + "'";
    }

    /**
     * Quote a path for safe use in Windows shell commands.
     * Wraps the path in double quotes and escapes any double quotes within.
     *
     * @param path The path to quote
     * @return Quoted path safe for Windows shell usage
     */
    public static String quoteWindowsPath(String path) {
        if (path == null) {
            return null;
        }
        // Escape double quotes by doubling them
        // Then wrap the whole path in double quotes
        return "\"" + path.replace("\"", "\"\"") + "\"";
    }

    /**
     * Build a safe Unix/Linux/macOS path with proper escaping for shell usage.
     * Uses $HOME instead of ~ because tilde doesn't expand in quotes.
     * Escapes spaces with backslashes for maximum compatibility.
     * Format: $HOME/.t1-super-ai/workspaces/{teamName}/agents/{agentName}/
     *
     * @param teamName The team name
     * @param agentName The agent name (can be null for team-level paths)
     * @return Properly escaped path string safe for shell usage
     */
    public static String buildQuotedUnixPath(String teamName, String agentName) {
        StringBuilder path = new StringBuilder("$HOME/.t1-super-ai/workspaces/");
        // Escape spaces in team name
        path.append(teamName.replace(" ", "\\ "));
        if (agentName != null) {
            path.append("/agents/");
            // Escape spaces in agent name
            path.append(agentName.replace(" ", "\\ "));
        }
        path.append("/");
        return path.toString();
    }

    /**
     * Build a safe Windows path with proper quoting for shell usage.
     * Wraps the entire path in double quotes to handle spaces.
     * Format: "%USERPROFILE%\.t1-super-ai\workspaces\{teamName}\agents\{agentName}\"
     *
     * @param teamName The team name
     * @param agentName The agent name (can be null for team-level paths)
     * @return Properly quoted path string safe for cmd/PowerShell usage
     */
    public static String buildQuotedWindowsPath(String teamName, String agentName) {
        StringBuilder path = new StringBuilder("%USERPROFILE%\\.t1-super-ai\\workspaces\\");
        path.append(teamName);
        if (agentName != null) {
            path.append("\\agents\\").append(agentName);
        }
        path.append("\\");
        return quoteWindowsPath(path.toString());
    }
}

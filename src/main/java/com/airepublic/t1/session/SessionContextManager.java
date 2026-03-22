package com.airepublic.t1.session;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.airepublic.t1.config.AgentConfigService;
import com.airepublic.t1.config.WorkspaceInitializer;
import com.airepublic.t1.service.MemoryManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages session context including CHARACTER.md, USAGE.md, and MEMORY.md
 * These files are automatically included as context for every session
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionContextManager {
    private final WorkspaceInitializer workspaceInitializer;
    private final AgentConfigService agentConfigService;

    @Autowired(required = false)
    private MemoryManager memoryManager;

    /**
     * Build initial context for a new session
     * Loads USER.md as system context
     * Note: CHARACTER.md files are agent-specific and loaded via buildInitialContext(agentName)
     */
    public String buildInitialContext() {
        final StringBuilder context = new StringBuilder();

        // Add USER.md if exists - user information applies to all agents
        final String userContent = workspaceInitializer.readUserMd();
        if (userContent != null) {
            context.append("# User Profile\n\n");
            context.append(userContent);
            context.append("\n");
            log.info("📋 Loaded USER.md as session context");
        } else {
            log.warn("⚠️ USER.md not found - user not configured");
            context.append("# Session Context\n\n");
            context.append("No user profile found. ");
            context.append("This may be the first run - consider running the HATCH setup process.\n");
        }

        return context.toString();
    }

    /**
     * Build context messages for LLM
     * Note: CHARACTER.md is agent-specific, use buildContextMessages(agentName) instead
     */
    public List<SessionMessage> buildContextMessages() {
        final List<SessionMessage> messages = new ArrayList<>();

        final String userContent = workspaceInitializer.readUserMd();
        if (userContent != null) {
            messages.add(new SessionMessage("system", "USER_PROFILE", userContent));
        }

        final String usageContent = workspaceInitializer.readUsageMd();
        if (usageContent != null) {
            messages.add(new SessionMessage("system", "USAGE_GUIDE", usageContent));
        }

        return messages;
    }

    /**
     * Get summary of loaded context
     * Note: CHARACTER.md is agent-specific, use getContextSummary(agentName) instead
     */
    public String getContextSummary() {
        final StringBuilder summary = new StringBuilder();
        summary.append("Session Context Loaded:\n");

        if (workspaceInitializer.readUserMd() != null) {
            summary.append("✅ USER.md - User profile and preferences\n");
        } else {
            summary.append("❌ USER.md - Not configured (run HATCH setup)\n");
        }

        if (workspaceInitializer.readUsageMd() != null) {
            summary.append("✅ USAGE.md - Configuration and usage guide\n");
        } else {
            summary.append("⚠️ USAGE.md - Not found\n");
        }

        return summary.toString();
    }

    /**
     * Build initial context for an agent-specific session
     * Loads CHARACTER.md, USAGE.md, and MEMORY.md from the agent's folder
     */
    public String buildInitialContext(String agentName) {
        final StringBuilder context = new StringBuilder();

        // Add clear agent identity statement
        context.append(String.format("YOU ARE: %s\n\n", agentName));

        // Add USER.md if exists - user information applies to all agents
        final String userContent = workspaceInitializer.readUserMd();
        if (userContent != null) {
            context.append("# User Profile\n\n");
            context.append(userContent);
            context.append("\n");
            log.info("📋 Loaded USER.md for agent '{}' as session context", agentName);
        } else {
            log.warn("⚠️ USER.md not found for agent '{}'", agentName);
        }

        // Try to load agent-specific CHARACTER.md
        try {
            String characterContent = agentConfigService.readCharacterMd(agentName);
            if (characterContent != null) {
                // Include the full CHARACTER.md content for the agent
                context.append(characterContent);
                context.append("\n");
                log.info("📋 Loaded CHARACTER.md for agent '{}' as session context", agentName);
            } else {
                log.warn("⚠️ CHARACTER.md not found for agent '{}'", agentName);
                context.append("No character profile found for this agent.\n");
            }
        } catch (Exception e) {
            log.error("Error loading CHARACTER.md for agent '{}'", agentName, e);
            context.append("Error loading character profile.\n");
        }

        // Load agent-specific MEMORY.md if available
        if (memoryManager != null) {
            try {
                String memoryContent = memoryManager.loadMemory(agentName);
                if (memoryContent != null && !memoryContent.isEmpty()) {
                    // Approximate token count (1 token ≈ 4 characters)
                    int approxTokens = memoryContent.length() / 4;

                    // Safety check: don't load if memory is extremely large
                    if (approxTokens > 10000) {
                        log.warn("⚠️ MEMORY.md for agent '{}' is very large (~{} tokens), skipping load to prevent context overflow",
                            agentName, approxTokens);
                        context.append("# Previous Conversation History\n\n");
                        context.append("*Memory file is too large to load. Recent conversations may need to be compacted.*\n\n");
                    } else {
                        context.append("# Previous Conversation History\n\n");
                        context.append(memoryContent);
                        context.append("\n");
                        long memorySize = memoryManager.getMemorySize(agentName);
                        log.info("📋 Loaded MEMORY.md for agent '{}' (~{} tokens, {} KB on disk)",
                            agentName, approxTokens, memorySize / 1024);
                    }
                } else {
                    log.debug("No memory found for agent '{}'", agentName);
                }
            } catch (Exception e) {
                log.error("Error loading MEMORY.md for agent '{}'", agentName, e);
            }
        }

        return context.toString();
    }

    /**
     * Extract key information from agent CHARACTER.md
     */
    private String extractAgentCharacterInfo(final String characterContent) {
        final StringBuilder info = new StringBuilder();

        final String[] lines = characterContent.split("\n");

        for (String line : lines) {
            line = line.trim();

            // Extract key fields for agent profile
            if (line.startsWith("- **Name:**")) {
                info.append("Agent Name: ").append(line.substring(11).trim()).append("\n");
            } else if (line.startsWith("- **Role:**")) {
                info.append("Agent Role: ").append(line.substring(11).trim()).append("\n");
            } else if (line.startsWith("- **Purpose:**")) {
                info.append("Agent Purpose: ").append(line.substring(14).trim()).append("\n");
            } else if (line.startsWith("## Personality")) {
                // Next non-empty line after "## Personality" is the personality description
                continue;
            } else if (line.startsWith("## Communication Style")) {
                // Next non-empty line is communication style
                continue;
            } else if (!line.startsWith("#") && !line.startsWith("-") && !line.isEmpty() && info.length() > 0) {
                // Capture description lines
                if (!info.toString().endsWith("\n\n")) {
                    info.append(line).append("\n");
                }
            }
        }

        return info.toString();
    }

    /**
     * Build context messages for agent-specific LLM
     */
    public List<SessionMessage> buildContextMessages(String agentName) {
        final List<SessionMessage> messages = new ArrayList<>();

        try {
            String characterContent = agentConfigService.readCharacterMd(agentName);
            if (characterContent != null) {
                messages.add(new SessionMessage("system", "CHARACTER_PROFILE", characterContent));
            }

            String usageContent = agentConfigService.readUsageMd(agentName);
            if (usageContent != null) {
                messages.add(new SessionMessage("system", "USAGE_GUIDE", usageContent));
            }
        } catch (Exception e) {
            log.error("Error loading context for agent '{}'", agentName, e);
        }

        return messages;
    }

    /**
     * Get summary of loaded context for a specific agent
     */
    public String getContextSummary(String agentName) {
        final StringBuilder summary = new StringBuilder();
        summary.append("Session Context for ").append(agentName).append(":\n");

        try {
            if (agentConfigService.readCharacterMd(agentName) != null) {
                summary.append("✅ CHARACTER.md - Agent personality and role\n");
            } else {
                summary.append("❌ CHARACTER.md - Not found\n");
            }

            if (agentConfigService.readUsageMd(agentName) != null) {
                summary.append("✅ USAGE.md - Usage guide\n");
            } else {
                summary.append("⚠️ USAGE.md - Not found\n");
            }
        } catch (Exception e) {
            summary.append("❌ Error loading context: ").append(e.getMessage()).append("\n");
        }

        return summary.toString();
    }

    /**
     * Session message structure
     */
    public static class SessionMessage {
        private final String role;
        private final String type;
        private final String content;

        public SessionMessage(final String role, final String type, final String content) {
            this.role = role;
            this.type = type;
            this.content = content;
        }

        public String getRole() { return role; }
        public String getType() { return type; }
        public String getContent() { return content; }
    }
}

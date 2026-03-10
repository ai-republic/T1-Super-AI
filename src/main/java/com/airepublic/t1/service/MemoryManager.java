package com.airepublic.t1.service;

import com.airepublic.t1.agent.LLMClientFactory;
import com.airepublic.t1.config.AgentConfigurationManager;
import com.airepublic.t1.memory.VectorMemoryService;
import com.airepublic.t1.memory.VectorStoreFactory;
import com.airepublic.t1.model.ConversationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages agent memory by storing conversations in MEMORY.md files
 * and optionally in vector store for semantic search.
 * Automatically compacts memory when it exceeds size limits.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryManager {

    private static final long MAX_MEMORY_SIZE_BYTES = 32 * 1024; // 32KB (~8K tokens)
    private static final long TARGET_MEMORY_SIZE_BYTES = 8 * 1024; // 8KB after compaction (~2K tokens)
    private static final long MAX_MEMORY_LOAD_BYTES = 16 * 1024; // Max 16KB to load into context (~4K tokens)
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AgentConfigurationManager configManager;

    @Autowired(required = false)
    private LLMClientFactory llmClientFactory;

    @Autowired(required = false)
    private VectorStoreFactory vectorStoreFactory;

    // Cache of per-agent VectorMemoryService instances
    private final Map<String, VectorMemoryService> agentVectorServices = new ConcurrentHashMap<>();

    /**
     * Get the path to an agent's memory file.
     */
    public Path getMemoryPath(String agentName) {
        String userHome = System.getProperty("user.home");
        Path agentsDir = Paths.get(userHome, ".t1-super-ai", "agents", agentName);
        return agentsDir.resolve("MEMORY.md");
    }

    /**
     * Load memory content for an agent with size limit.
     * If memory exceeds limit, only loads recent content.
     * If vector memory is available, also includes relevant context from semantic search.
     */
    public String loadMemory(String agentName) {
        try {
            Path memoryPath = getMemoryPath(agentName);
            if (!Files.exists(memoryPath)) {
                log.debug("No memory file found for agent '{}'", agentName);
                return "";
            }

            long fileSize = Files.size(memoryPath);

            // If file is within load limit, load it all
            if (fileSize <= MAX_MEMORY_LOAD_BYTES) {
                String content = Files.readString(memoryPath);
                log.debug("Loaded full memory for agent '{}': {} bytes", agentName, content.length());
                return content;
            }

            // File is too large, only load recent portion
            String fullContent = Files.readString(memoryPath);
            String truncated = truncateMemoryForContext(fullContent, agentName);
            log.warn("Memory for agent '{}' is {}KB, truncated to {} bytes for context",
                agentName, fileSize / 1024, truncated.length());
            return truncated;

        } catch (IOException e) {
            log.error("Failed to load memory for agent '{}'", agentName, e);
            return "";
        }
    }

    /**
     * Get or create VectorMemoryService for an agent.
     */
    private VectorMemoryService getVectorMemoryService(String agentName) {
        if (vectorStoreFactory == null) {
            return null;
        }

        return agentVectorServices.computeIfAbsent(agentName, name -> {
            try {
                VectorStore vectorStore = vectorStoreFactory.getVectorStore(name);
                return new VectorMemoryService(vectorStore);
            } catch (Exception e) {
                log.error("Failed to create VectorMemoryService for agent '{}'", name, e);
                return null;
            }
        });
    }

    /**
     * Load memory with semantic context enhancement.
     * Uses vector search to find relevant past conversations based on the query.
     */
    public String loadMemoryWithContext(String agentName, String currentQuery) {
        String baseMemory = loadMemory(agentName);

        // If vector memory is available, enhance with semantic search
        VectorMemoryService vectorMemoryService = getVectorMemoryService(agentName);
        if (vectorMemoryService != null && currentQuery != null && !currentQuery.isEmpty()) {
            try {
                String relevantContext = vectorMemoryService.getRelevantContext(currentQuery, 3);
                if (!relevantContext.isEmpty()) {
                    log.debug("Enhanced memory for agent '{}' with vector context", agentName);
                    return baseMemory + "\n\n" + relevantContext;
                }
            } catch (Exception e) {
                log.warn("Failed to enhance memory with vector context for agent '{}'", agentName, e);
            }
        }

        return baseMemory;
    }

    /**
     * Truncate memory to fit within context limit.
     * Keeps header and most recent conversations.
     */
    private String truncateMemoryForContext(String fullMemory, String agentName) {
        // Target: Keep within MAX_MEMORY_LOAD_BYTES
        if (fullMemory.length() <= MAX_MEMORY_LOAD_BYTES) {
            return fullMemory;
        }

        // Split by conversation separator
        String[] parts = fullMemory.split("---\n\n");

        if (parts.length <= 2) {
            // Just header and one conversation, truncate the conversation
            return fullMemory.substring(0, (int) MAX_MEMORY_LOAD_BYTES);
        }

        // Keep header (first part) and most recent conversations
        StringBuilder result = new StringBuilder();
        result.append(parts[0]).append("---\n\n");

        // Add warning about truncation
        result.append("**Note**: Earlier conversations not shown due to size limit.\n\n---\n\n");

        // Add recent conversations from the end, working backwards
        int targetSize = (int) MAX_MEMORY_LOAD_BYTES - result.length() - 100; // Leave some buffer
        StringBuilder recentParts = new StringBuilder();

        for (int i = parts.length - 1; i >= 1; i--) {
            String part = parts[i];
            if (recentParts.length() + part.length() + 5 <= targetSize) {
                // Prepend (we're going backwards)
                if (recentParts.length() > 0) {
                    recentParts.insert(0, "---\n\n");
                }
                recentParts.insert(0, part);
            } else {
                break;
            }
        }

        result.append(recentParts);
        return result.toString();
    }

    /**
     * Append a conversation exchange to the agent's memory.
     * Also stores in vector memory if available for semantic search.
     */
    public void appendConversation(String agentName, String userMessage, String assistantResponse) {
        try {
            Path memoryPath = getMemoryPath(agentName);

            // Ensure directory exists
            Files.createDirectories(memoryPath.getParent());

            // Format the conversation entry
            String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
            StringBuilder entry = new StringBuilder();

            if (!Files.exists(memoryPath)) {
                // Create new memory file with header
                entry.append("# Memory for ").append(agentName).append("\n\n");
                entry.append("This file contains the conversation history for this agent.\n");
                entry.append("It is automatically maintained and compacted when it grows too large.\n\n");
                entry.append("---\n\n");
            }

            entry.append("## ").append(timestamp).append("\n\n");
            entry.append("**User**: ").append(userMessage).append("\n\n");
            entry.append("**Assistant**: ").append(assistantResponse).append("\n\n");
            entry.append("---\n\n");

            // Append to file
            Files.writeString(memoryPath, entry.toString(),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);

            log.debug("Appended conversation to memory for agent '{}'", agentName);

            // Store in vector memory if available
            VectorMemoryService vectorMemoryService = getVectorMemoryService(agentName);
            if (vectorMemoryService != null) {
                try {
                    vectorMemoryService.storePrompt(userMessage, assistantResponse, agentName, "agent");
                } catch (Exception e) {
                    log.warn("Failed to store conversation in vector memory for agent '{}'", agentName, e);
                }
            }

            // Check if compaction is needed
            checkAndCompactMemory(agentName);

        } catch (IOException e) {
            log.error("Failed to append conversation to memory for agent '{}'", agentName, e);
        }
    }

    /**
     * Append conversation messages to the agent's memory.
     */
    public void appendConversationMessages(String agentName, List<ConversationMessage> messages) {
        if (messages.isEmpty()) {
            return;
        }

        // Group messages into user-assistant pairs
        String currentUser = null;
        StringBuilder responses = new StringBuilder();

        for (ConversationMessage msg : messages) {
            if ("user".equals(msg.getRole())) {
                // If we have a previous pair, save it
                if (currentUser != null && responses.length() > 0) {
                    appendConversation(agentName, currentUser, responses.toString().trim());
                    responses = new StringBuilder();
                }
                currentUser = msg.getContent();
            } else if ("assistant".equals(msg.getRole())) {
                if (responses.length() > 0) {
                    responses.append("\n\n");
                }
                responses.append(msg.getContent());
            }
        }

        // Save the last pair if exists
        if (currentUser != null && responses.length() > 0) {
            appendConversation(agentName, currentUser, responses.toString().trim());
        }
    }

    /**
     * Check memory size and compact if necessary.
     */
    private void checkAndCompactMemory(String agentName) {
        try {
            Path memoryPath = getMemoryPath(agentName);

            if (!Files.exists(memoryPath)) {
                return;
            }

            long fileSize = Files.size(memoryPath);

            if (fileSize > MAX_MEMORY_SIZE_BYTES) {
                log.info("Memory for agent '{}' exceeds {}KB, compacting...",
                    agentName, MAX_MEMORY_SIZE_BYTES / 1024);
                compactMemory(agentName);
            }

        } catch (IOException e) {
            log.error("Failed to check memory size for agent '{}'", agentName, e);
        }
    }

    /**
     * Compact memory by summarizing with LLM.
     */
    private void compactMemory(String agentName) {
        try {
            // Check if LLM client factory is available
            if (llmClientFactory == null) {
                log.warn("LLMClientFactory not available, cannot compact memory for agent '{}'", agentName);
                return;
            }

            Path memoryPath = getMemoryPath(agentName);
            String currentMemory = Files.readString(memoryPath);

            // Use LLM to summarize the conversation history
            String summarizationPrompt = String.format("""
                You are summarizing a conversation history for an AI agent named '%s'.

                Your task is to create a concise summary that preserves:
                1. Key facts, decisions, and outcomes
                2. Important context about the user and their goals
                3. Preferences and patterns that emerged
                4. Unresolved issues or ongoing tasks

                The summary should be approximately %d characters (about %dKB).
                Be concise but preserve essential information.

                Current conversation history:

                %s

                Provide a well-structured markdown summary that the agent can use to recall this conversation.
                """,
                agentName,
                TARGET_MEMORY_SIZE_BYTES,
                TARGET_MEMORY_SIZE_BYTES / 1024,
                currentMemory);

            // Get ChatClient from factory using default provider
            ChatClient chatClient = llmClientFactory.getChatClient(configManager.getConfiguration().getDefaultProvider());
            String summary = chatClient.prompt(new Prompt(new UserMessage(summarizationPrompt)))
                .call()
                .content();

            // Create new memory file with summary
            StringBuilder newMemory = new StringBuilder();
            newMemory.append("# Memory for ").append(agentName).append("\n\n");
            newMemory.append("This file contains the conversation history for this agent.\n");
            newMemory.append("It is automatically maintained and compacted when it grows too large.\n\n");
            newMemory.append("**Last Compaction**: ").append(LocalDateTime.now().format(TIMESTAMP_FORMAT)).append("\n\n");
            newMemory.append("---\n\n");
            newMemory.append("## Summary of Previous Conversations\n\n");
            newMemory.append(summary).append("\n\n");
            newMemory.append("---\n\n");

            // Write compacted memory
            Files.writeString(memoryPath, newMemory.toString());

            long oldSize = currentMemory.length();
            long newSize = newMemory.length();
            log.info("Compacted memory for agent '{}': {}KB → {}KB ({}% reduction)",
                agentName,
                oldSize / 1024,
                newSize / 1024,
                (100 - (newSize * 100 / oldSize)));

        } catch (Exception e) {
            log.error("Failed to compact memory for agent '{}'", agentName, e);
        }
    }

    /**
     * Manually trigger memory compaction for an agent.
     * Useful when memory has grown too large.
     */
    public void forceCompaction(String agentName) {
        try {
            Path memoryPath = getMemoryPath(agentName);
            if (Files.exists(memoryPath)) {
                long fileSize = Files.size(memoryPath);
                log.info("Forcing compaction for agent '{}' (current size: {} KB)", agentName, fileSize / 1024);
                compactMemory(agentName);
            } else {
                log.warn("No memory file to compact for agent '{}'", agentName);
            }
        } catch (IOException e) {
            log.error("Failed to force compaction for agent '{}'", agentName, e);
        }
    }

    /**
     * Clear memory for an agent.
     */
    public void clearMemory(String agentName) {
        try {
            Path memoryPath = getMemoryPath(agentName);
            if (Files.exists(memoryPath)) {
                Files.delete(memoryPath);
                log.info("Cleared memory for agent '{}'", agentName);
            }
        } catch (IOException e) {
            log.error("Failed to clear memory for agent '{}'", agentName, e);
        }
    }

    /**
     * Get memory file size in bytes.
     */
    public long getMemorySize(String agentName) {
        try {
            Path memoryPath = getMemoryPath(agentName);
            if (Files.exists(memoryPath)) {
                return Files.size(memoryPath);
            }
        } catch (IOException e) {
            log.error("Failed to get memory size for agent '{}'", agentName, e);
        }
        return 0;
    }
}

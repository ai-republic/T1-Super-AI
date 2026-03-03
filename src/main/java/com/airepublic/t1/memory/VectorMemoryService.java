package com.airepublic.t1.memory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for storing and retrieving conversation history using a vector database.
 *
 * This service uses embeddings to store prompts and actions, enabling:
 * - Semantic search of past conversations
 * - Retrieval of similar past interactions
 * - Long-term memory across sessions
 * - Context-aware responses based on history
 *
 * NOTE: This service is only created if a VectorStore bean is available.
 */
@Slf4j
@Service
@ConditionalOnBean(VectorStore.class)
public class VectorMemoryService {

    private final VectorStore vectorStore;
    private final String memoryStorePath;
    private static final int DEFAULT_TOP_K = 5;
    private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.7;

    public VectorMemoryService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
        // Use Paths.get() for cross-platform compatibility (Windows, Linux, macOS)
        this.memoryStorePath = java.nio.file.Paths.get(System.getProperty("user.home"), ".t1-super-ai", "memory.json").toString();

        // Load existing memories from disk if available (only for SimpleVectorStore)
        loadMemories();

        log.info("📚 Vector memory service initialized with {} documents", getMemoryCount());
    }

    /**
     * Store a user prompt with metadata
     */
    public void storePrompt(String prompt, String response, String provider, String model) {
        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("type", "prompt");
            metadata.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            metadata.put("provider", provider);
            metadata.put("model", model);
            metadata.put("response_preview", truncate(response, 200));

            Document document = new Document(
                "USER: " + prompt + "\nASSISTANT: " + response,
                metadata
            );

            vectorStore.add(List.of(document));
            log.debug("💾 Stored prompt: {}", truncate(prompt, 50));

            // Auto-save periodically
            saveMemories();
        } catch (Exception e) {
            log.error("Error storing prompt in vector memory", e);
        }
    }

    /**
     * Store a tool action with metadata
     */
    public void storeAction(String toolName, String toolInput, String toolOutput) {
        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("type", "action");
            metadata.put("tool", toolName);
            metadata.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            Document document = new Document(
                "TOOL: " + toolName + "\nINPUT: " + toolInput + "\nOUTPUT: " + truncate(toolOutput, 500),
                metadata
            );

            vectorStore.add(List.of(document));
            log.debug("🔧 Stored action: {}", toolName);

            saveMemories();
        } catch (Exception e) {
            log.error("Error storing action in vector memory", e);
        }
    }

    /**
     * Search for similar past conversations
     */
    public List<MemoryResult> searchSimilar(String query, int topK) {
        try {
            SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(DEFAULT_SIMILARITY_THRESHOLD)
                .build();

            List<Document> results = vectorStore.similaritySearch(request);

            return results.stream()
                .map(doc -> new MemoryResult(
                    doc.getText(),
                    doc.getMetadata(),
                    doc.getScore() != null ? doc.getScore() : 1.0
                ))
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error searching vector memory", e);
            return Collections.emptyList();
        }
    }

    /**
     * Search for similar past conversations with default top K
     */
    public List<MemoryResult> searchSimilar(String query) {
        return searchSimilar(query, DEFAULT_TOP_K);
    }

    /**
     * Get relevant context for a query
     */
    public String getRelevantContext(String query, int maxResults) {
        List<MemoryResult> results = searchSimilar(query, maxResults);

        if (results.isEmpty()) {
            return "";
        }

        StringBuilder context = new StringBuilder();
        context.append("📚 Relevant past interactions:\n\n");

        for (int i = 0; i < results.size(); i++) {
            MemoryResult result = results.get(i);
            context.append(String.format("[%d] %s\n", i + 1,
                truncate(result.content, 200)));
            context.append(String.format("    ⏰ %s\n\n",
                result.metadata.get("timestamp")));
        }

        return context.toString();
    }

    /**
     * Get statistics about stored memories
     */
    public MemoryStats getStats() {
        // SimpleVectorStore doesn't provide direct count, so we estimate
        List<MemoryResult> all = searchSimilar("", 1000);

        long prompts = all.stream()
            .filter(m -> "prompt".equals(m.metadata().get("type")))
            .count();

        long actions = all.stream()
            .filter(m -> "action".equals(m.metadata().get("type")))
            .count();

        return new MemoryStats(prompts, actions, prompts + actions);
    }

    /**
     * Clear all memories
     */
    public void clearMemories() {
        try {
            // Create new vector store (SimpleVectorStore doesn't have clear method)
            File memoryFile = new File(memoryStorePath);
            if (memoryFile.exists()) {
                memoryFile.delete();
            }
            log.info("🗑️ All memories cleared");
        } catch (Exception e) {
            log.error("Error clearing memories", e);
        }
    }

    /**
     * Save memories to disk
     */
    public void saveMemories() {
        try {
            if (vectorStore instanceof SimpleVectorStore simpleStore) {
                File memoryFile = new File(memoryStorePath);
                memoryFile.getParentFile().mkdirs();
                simpleStore.save(memoryFile);
                log.debug("💾 Memories saved to {}", memoryStorePath);
            }
        } catch (Exception e) {
            log.error("Error saving memories to disk", e);
        }
    }

    /**
     * Load memories from disk
     */
    private void loadMemories() {
        try {
            File memoryFile = new File(memoryStorePath);
            if (memoryFile.exists() && vectorStore instanceof SimpleVectorStore simpleStore) {
                simpleStore.load(memoryFile);
                log.info("📂 Loaded existing memories from {}", memoryStorePath);
            }
        } catch (Exception e) {
            log.warn("Could not load existing memories, starting fresh", e);
        }
    }

    /**
     * Get memory count (approximate)
     */
    private int getMemoryCount() {
        try {
            return searchSimilar("", 1000).size();
        } catch (Exception e) {
            return 0;
        }
    }

    // Helper methods

    private String truncate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...";
    }

    // Inner classes

    public record MemoryResult(String content, Map<String, Object> metadata, double score) {
    }

    public record MemoryStats(long promptCount, long actionCount, long totalCount) {
    }
}

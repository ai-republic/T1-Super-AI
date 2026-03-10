package com.airepublic.t1.memory;

import com.airepublic.vectorstore.DistanceMetric;
import com.airepublic.vectorstore.spring.SpringAIVectorStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for creating per-agent VectorStore instances.
 * <p>
 * Each agent gets its own SpringAIVectorStore with:
 * - Collection named after the agent
 * - Storage in ~/.t1-super-ai/agents/{agentName}/vectorstore/
 * - Shared EmbeddingModel from configuration
 * <p>
 * VectorStore instances are cached and reused per agent.
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "spring.ai.vectorstore", name = "enabled", havingValue = "true")
@ConditionalOnBean(EmbeddingModel.class)
public class VectorStoreFactory {

    private final EmbeddingModel embeddingModel;
    private final Map<String, VectorStore> agentVectorStores = new ConcurrentHashMap<>();
    private final int dimension;
    private final DistanceMetric metric;

    public VectorStoreFactory(
            EmbeddingModel embeddingModel,
            @org.springframework.beans.factory.annotation.Value("${spring.ai.vectorstore.filesystem.dimension:1536}") int dimension,
            @org.springframework.beans.factory.annotation.Value("${spring.ai.vectorstore.filesystem.metric:cosine}") String metricName) {
        this.embeddingModel = embeddingModel;
        this.dimension = dimension;
        this.metric = parseMetric(metricName);
        log.info("VectorStoreFactory initialized (dimension={}, metric={})", dimension, metric);
    }

    /**
     * Get or create a VectorStore for the specified agent.
     * VectorStore instances are cached per agent.
     *
     * @param agentName The agent name (used as collection name)
     * @return VectorStore instance for this agent
     */
    public VectorStore getVectorStore(String agentName) {
        return agentVectorStores.computeIfAbsent(agentName, this::createVectorStore);
    }

    /**
     * Create a new VectorStore instance for an agent.
     */
    private VectorStore createVectorStore(String agentName) {
        try {
            String userHome = System.getProperty("user.home");
            Path storagePath = Paths.get(userHome, ".t1-super-ai", "agents", agentName, "vectorstore");

            log.info("Creating VectorStore for agent '{}' at: {}", agentName, storagePath);

            return new SpringAIVectorStore(
                storagePath,
                agentName, // Use agent name as collection name
                dimension,
                metric,
                embeddingModel
            );

        } catch (IOException e) {
            log.error("Failed to create VectorStore for agent '{}'", agentName, e);
            throw new RuntimeException("Failed to initialize VectorStore for agent: " + agentName, e);
        }
    }

    /**
     * Close and remove a VectorStore for an agent.
     * Useful for cleanup or when an agent is deleted.
     */
    public void closeVectorStore(String agentName) {
        VectorStore vectorStore = agentVectorStores.remove(agentName);
        if (vectorStore instanceof SpringAIVectorStore springAIVectorStore) {
            try {
                springAIVectorStore.close();
                log.info("Closed VectorStore for agent '{}'", agentName);
            } catch (Exception e) {
                log.error("Error closing VectorStore for agent '{}'", agentName, e);
            }
        }
    }

    /**
     * Close all VectorStore instances.
     * Called on application shutdown.
     */
    public void closeAll() {
        agentVectorStores.forEach((agentName, vectorStore) -> {
            if (vectorStore instanceof SpringAIVectorStore springAIVectorStore) {
                try {
                    springAIVectorStore.close();
                    log.debug("Closed VectorStore for agent '{}'", agentName);
                } catch (Exception e) {
                    log.error("Error closing VectorStore for agent '{}'", agentName, e);
                }
            }
        });
        agentVectorStores.clear();
        log.info("Closed all VectorStore instances");
    }

    /**
     * Parse distance metric from configuration.
     */
    private DistanceMetric parseMetric(String metricName) {
        return switch (metricName.toLowerCase()) {
            case "l2", "euclidean" -> DistanceMetric.L2;
            case "ip", "inner_product", "dot" -> DistanceMetric.INNER_PRODUCT;
            case "cosine", "cos" -> DistanceMetric.COSINE;
            default -> DistanceMetric.COSINE;
        };
    }
}

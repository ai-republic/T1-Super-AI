package com.airepublic.t1.service;

import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.airepublic.t1.agent.LLMClientFactory;
import com.airepublic.t1.config.AgentConfigurationManager;
import com.airepublic.t1.model.AgentConfiguration.TaskType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * LLM-based task classifier that uses the general model to intelligently classify
 * user prompts into specific task types.
 *
 * If classification fails or times out, returns empty Optional to use the general/default model.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LLMTaskClassifier {

    private final LLMClientFactory llmClientFactory;
    private final AgentConfigurationManager configManager;

    @Value("${ai-agent.auto-model.classification-timeout:300000}")
    private long classificationTimeoutMs;

    private final ExecutorService executorService = Executors.newCachedThreadPool();

    /**
     * Classifies a user prompt into a TaskType using the general LLM model.
     * Falls back to using the general/default model if LLM classification fails.
     *
     * @param userPrompt The user's prompt to classify
     * @return Optional containing the detected TaskType, or empty to use general/default model
     */
    public Optional<TaskType> classifyPrompt(final String userPrompt) {
        if (userPrompt == null || userPrompt.trim().isEmpty()) {
            return Optional.empty();
        }

        try {
            // Try LLM-based classification with timeout (30 seconds for Ollama)
            final Optional<TaskType> llmResult = classifyWithLLMTimeout(userPrompt);
            if (llmResult.isPresent()) {
                log.info("LLM classified prompt as: {}", llmResult.get());
                return llmResult;
            }
        } catch (final TimeoutException e) {
            log.warn("LLM classification timeout ({}ms), using general model", classificationTimeoutMs);
        } catch (final Exception e) {
            log.warn("LLM classification failed, using general model: {}", e.getMessage());
        }

        // Fallback to general/default model (return empty)
        log.debug("No specific task type detected, using general model");
        return Optional.empty();
    }

    /**
     * Classifies with LLM using a timeout to prevent hanging.
     */
    private Optional<TaskType> classifyWithLLMTimeout(final String userPrompt) throws TimeoutException, ExecutionException, InterruptedException {
        final Future<Optional<TaskType>> future = executorService.submit(() -> classifyWithLLM(userPrompt));

        try {
            return future.get(classificationTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (final TimeoutException e) {
            future.cancel(true);
            throw e;
        }
    }

    /**
     * Uses the general LLM model to classify the prompt into a TaskType.
     */
    private Optional<TaskType> classifyWithLLM(final String userPrompt) {
        // Get the default/general chat client
        final ChatClient chatClient = llmClientFactory.getChatClient(
                configManager.getConfiguration().getDefaultProvider()
                );

        // Build classification prompt
        final String classificationPrompt = buildClassificationPrompt(userPrompt);

        log.debug("Sending classification request to LLM");

        // Get classification from LLM
        final String response = chatClient.prompt(classificationPrompt)
                .call()
                .content();

        // Parse the response
        return parseTaskTypeResponse(response);
    }

    /**
     * Builds the classification prompt for the LLM.
     * Optimized to be concise for faster processing.
     */
    private String buildClassificationPrompt(final String userPrompt) {
        // Build compact list of task types
        final String taskTypeList = Arrays.stream(TaskType.values())
                .map(tt -> String.format("%s (%s)", tt.name(), tt.getDescription()))
                .collect(Collectors.joining(", "));

        return String.format("""
                Classify this prompt into ONE task type. Respond with ONLY ONE WORD - the exact task type name, or 'GENERAL' if none fit.

                Do NOT include explanations, punctuation, or extra text. Just the single word task type.

                Task types: %s

                Prompt: "%s"

                Your one-word answer:""", taskTypeList, userPrompt);
    }

    /**
     * Parses the LLM response to extract the TaskType.
     */
    private Optional<TaskType> parseTaskTypeResponse(final String response) {
        if (response == null || response.trim().isEmpty()) {
            return Optional.empty();
        }

        // Try multiple parsing strategies
        String cleaned = response.trim().toUpperCase();

        // Strategy 1: Extract first line only (handle multi-line responses)
        final String[] lines = cleaned.split("[\\r\\n]+");
        if (lines.length > 0) {
            cleaned = lines[0].trim();
        }

        // Strategy 2: Extract after common prefixes like "Type:", "TASK_TYPE:", etc.
        if (cleaned.contains(":")) {
            final String[] parts = cleaned.split(":", 2);
            if (parts.length == 2) {
                cleaned = parts[1].trim();
            }
        }

        // Strategy 3: Extract first word/token only
        final String[] tokens = cleaned.split("\\s+");
        if (tokens.length > 0) {
            cleaned = tokens[0].trim();
        }

        // Strategy 4: Remove all non-alphanumeric characters except underscore
        cleaned = cleaned.replaceAll("[^A-Z_]", "");

        // Check for "GENERAL" or empty response
        if (cleaned.isEmpty() || cleaned.equals("GENERAL") || cleaned.equals("DEFAULT")) {
            log.debug("LLM classified as general/default task");
            return Optional.empty();
        }

        // Try to match to TaskType
        try {
            final TaskType taskType = TaskType.valueOf(cleaned);
            log.debug("Successfully parsed TaskType: {}", taskType);
            return Optional.of(taskType);
        } catch (final IllegalArgumentException e) {
            log.warn("LLM returned unknown task type: '{}' from original response: '{}', will use default",
                    cleaned, response.replaceAll("[\\r\\n]+", " ").substring(0, Math.min(100, response.length())));
            return Optional.empty();
        }
    }

    /**
     * Gets a detailed classification with confidence explanation (for debugging).
     */
    public String classifyWithExplanation(final String userPrompt) {
        if (userPrompt == null || userPrompt.trim().isEmpty()) {
            return "No prompt provided";
        }

        try {
            final ChatClient chatClient = llmClientFactory.getChatClient(
                    configManager.getConfiguration().getDefaultProvider()
                    );

            final String taskTypeList = Arrays.stream(TaskType.values())
                    .map(tt -> String.format("- %s: %s", tt.name(), tt.getDescription()))
                    .collect(Collectors.joining("\n"));

            final String prompt = String.format("""
                    You are a task classifier for an AI agent system. Analyze the following user prompt and determine which specialized task type it belongs to.

                    Available Task Types:
                    %s

                    User Prompt:
                    "%s"

                    Provide:
                    1. The task type (or GENERAL if no specific type fits)
                    2. Your reasoning for this classification
                    3. Confidence level (LOW/MEDIUM/HIGH)

                    Format your response as:
                    TASK_TYPE: [type]
                    REASONING: [why you chose this type]
                    CONFIDENCE: [level]
                    """, taskTypeList, userPrompt);

            return chatClient.prompt(prompt)
                    .call()
                    .content();

        } catch (final Exception e) {
            return "Error during classification: " + e.getMessage();
        }
    }
}

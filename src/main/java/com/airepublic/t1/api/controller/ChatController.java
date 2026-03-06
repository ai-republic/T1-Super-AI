package com.airepublic.t1.api.controller;

import com.airepublic.t1.agent.AgentManager;
import com.airepublic.t1.agent.AgentOrchestrator;
import com.airepublic.t1.api.dto.ApiResponse;
import com.airepublic.t1.api.dto.ChatRequest;
import com.airepublic.t1.api.dto.ChatResponse;
import com.airepublic.t1.config.AgentConfigurationManager;
import com.airepublic.t1.model.ConversationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {
    private final AgentOrchestrator orchestrator;
    private final AgentManager agentManager;
    private final AgentConfigurationManager configManager;
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    @PostMapping
    public ResponseEntity<ApiResponse<ChatResponse>> sendMessage(@RequestBody ChatRequest request) {
        log.info("Received chat request: {}", request.getMessage());

        try {
            long startTime = System.currentTimeMillis();

            // Switch agent if specified
            if (request.getAgentName() != null && !request.getAgentName().isEmpty()) {
                if (!agentManager.getCurrentAgentName().equals(request.getAgentName())) {
                    agentManager.switchToAgent(request.getAgentName());
                }
            }

            // Process message
            String response = orchestrator.processMessage(request.getMessage());
            long responseTime = System.currentTimeMillis() - startTime;

            // Build response
            ChatResponse chatResponse = ChatResponse.builder()
                    .response(response)
                    .agentName(agentManager.getCurrentAgentName())
                    .modelUsed(configManager.getCurrentModel())
                    .timestamp(LocalDateTime.now())
                    .responseTimeMs(responseTime)
                    .build();

            return ResponseEntity.ok(ApiResponse.success(chatResponse));

        } catch (Exception e) {
            log.error("Error processing chat message", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Error processing message: " + e.getMessage()));
        }
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMessage(@RequestParam String message,
                                   @RequestParam(required = false) String agentName) {
        log.debug("SSE stream request for message: {}", message.substring(0, Math.min(50, message.length())));
        SseEmitter emitter = new SseEmitter(300000L); // 5 minute timeout

        executorService.execute(() -> {
            long startTime = System.currentTimeMillis();

            try {
                // Switch agent if specified
                if (agentName != null && !agentName.isEmpty()) {
                    if (!agentManager.getCurrentAgentName().equals(agentName)) {
                        agentManager.switchToAgent(agentName);
                    }
                }

                // Send start event
                emitter.send(SseEmitter.event()
                        .name("start")
                        .data("Processing message..."));

                // Process message (currently blocking - need to implement streaming)
                String response = orchestrator.processMessage(message);

                // Send response in chunks (7 words at a time)
                String[] words = response.split("\\s+");
                int chunkSize = 7;

                for (int i = 0; i < words.length; i += chunkSize) {
                    int end = Math.min(i + chunkSize, words.length);
                    String chunk = String.join(" ", java.util.Arrays.copyOfRange(words, i, end));

                    // Add space after chunk if not the last chunk
                    if (end < words.length) {
                        chunk += " ";
                    }

                    emitter.send(SseEmitter.event()
                            .name("chunk")
                            .data(chunk));

                    Thread.sleep(50);
                }

                // Calculate response time
                long responseTime = System.currentTimeMillis() - startTime;

                // Send complete event with JSON metadata
                String completeData = String.format(
                    "{\"tokensUsed\":0,\"processingTime\":%d}",
                    responseTime
                );
                emitter.send(SseEmitter.event()
                        .name("complete")
                        .data(completeData));

                emitter.complete();
                log.debug("Stream completed in {}ms", responseTime);

            } catch (Exception e) {
                log.error("Error streaming message: {}", e.getMessage());
                try {
                    emitter.send(SseEmitter.event()
                            .name("chunk")
                            .data("\n\n*Error: " + e.getMessage() + "*"));
                    emitter.send(SseEmitter.event()
                            .name("complete")
                            .data("{}"));
                } catch (Exception sendError) {
                    log.error("Error sending error message", sendError);
                }
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<ConversationMessage>>> getHistory() {
        try {
            List<ConversationMessage> history = orchestrator.getConversationHistory();
            return ResponseEntity.ok(ApiResponse.success(history));
        } catch (Exception e) {
            log.error("Error retrieving conversation history", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Error retrieving history: " + e.getMessage()));
        }
    }

    @DeleteMapping("/history")
    public ResponseEntity<ApiResponse<Void>> clearHistory() {
        try {
            orchestrator.clearHistory();
            return ResponseEntity.ok(ApiResponse.success("Conversation history cleared", null));
        } catch (Exception e) {
            log.error("Error clearing conversation history", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Error clearing history: " + e.getMessage()));
        }
    }

    @PostMapping("/reload-context")
    public ResponseEntity<ApiResponse<Void>> reloadContext() {
        try {
            orchestrator.reloadSessionContext();
            return ResponseEntity.ok(ApiResponse.success("Session context reloaded", null));
        } catch (Exception e) {
            log.error("Error reloading context", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Error reloading context: " + e.getMessage()));
        }
    }
}

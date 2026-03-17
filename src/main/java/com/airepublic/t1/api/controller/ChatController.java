package com.airepublic.t1.api.controller;

import com.airepublic.t1.agent.AgentManager;
import com.airepublic.t1.agent.AgentOrchestrator;
import com.airepublic.t1.api.dto.ApiResponse;
import com.airepublic.t1.api.dto.ChatRequest;
import com.airepublic.t1.api.dto.ChatResponse;
import com.airepublic.t1.config.AgentConfigurationManager;
import com.airepublic.t1.model.ConversationMessage;
import com.airepublic.t1.model.MessageAttachment;
import com.airepublic.t1.service.FileAttachmentService;
import com.airepublic.t1.service.MemoryManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
    private final FileAttachmentService fileAttachmentService;
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    @Autowired(required = false)
    private MemoryManager memoryManager;

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

            // Process message with attachments
            String response = orchestrator.processMessage(request.getMessage(), request.getAttachments());
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

    /**
     * Send a message with file attachments (multipart form data)
     */
    @PostMapping(value = "/with-files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ChatResponse>> sendMessageWithFiles(
            @RequestParam String message,
            @RequestParam(required = false) MultipartFile[] files,
            @RequestParam(required = false) String agentName) {

        log.info("Received chat request with {} file(s): {}",
                 files != null ? files.length : 0,
                 message.substring(0, Math.min(50, message.length())));

        try {
            long startTime = System.currentTimeMillis();

            // Switch agent if specified
            if (agentName != null && !agentName.isEmpty()) {
                if (!agentManager.getCurrentAgentName().equals(agentName)) {
                    agentManager.switchToAgent(agentName);
                }
            }

            // Process uploaded files
            List<MessageAttachment> attachments = new ArrayList<>();
            if (files != null && files.length > 0) {
                for (MultipartFile file : files) {
                    try {
                        MessageAttachment attachment = fileAttachmentService.processFile(file);
                        attachments.add(attachment);
                        log.debug("Processed file: {} ({} bytes)", attachment.getFilename(), attachment.getFileSize());
                    } catch (Exception e) {
                        log.error("Error processing file: {}", file.getOriginalFilename(), e);
                        return ResponseEntity.badRequest()
                                .body(ApiResponse.error("Error processing file " + file.getOriginalFilename() + ": " + e.getMessage()));
                    }
                }
            }

            // Process message with attachments
            String response = orchestrator.processMessage(message, attachments);
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
            log.error("Error processing chat message with files", e);
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

                // Send complete event with JSON metadata including model used
                String modelUsed = configManager.getCurrentModel();
                String completeData = String.format(
                    "{\"tokensUsed\":0,\"processingTime\":%d,\"modelUsed\":\"%s\"}",
                    responseTime,
                    modelUsed != null ? modelUsed : ""
                );
                emitter.send(SseEmitter.event()
                        .name("complete")
                        .data(completeData));

                emitter.complete();
                log.debug("Stream completed in {}ms", responseTime);

            } catch (Exception e) {
                log.error("Error streaming message: {}", e.getMessage(), e);
                try {
                    // Send error message as a chunk
                    emitter.send(SseEmitter.event()
                            .name("chunk")
                            .data("\n\n**Error:** " + e.getMessage()));

                    // CRITICAL: Always send complete event to finalize the message
                    long responseTime = System.currentTimeMillis() - startTime;
                    String completeData = String.format(
                        "{\"tokensUsed\":0,\"processingTime\":%d,\"error\":true}",
                        responseTime
                    );
                    emitter.send(SseEmitter.event()
                            .name("complete")
                            .data(completeData));

                    // Complete normally (not with error) since we handled it gracefully
                    emitter.complete();
                    log.debug("Stream completed with error in {}ms", responseTime);
                } catch (Exception sendError) {
                    log.error("Error sending error message", sendError);
                    // Last resort - complete with error
                    emitter.completeWithError(e);
                }
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

    @PostMapping("/memory/compact")
    public ResponseEntity<ApiResponse<Void>> compactMemory(@RequestParam(required = false) String agentName) {
        try {
            if (memoryManager == null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Memory manager not available"));
            }

            String targetAgent = agentName != null ? agentName : agentManager.getCurrentAgentName();
            memoryManager.forceCompaction(targetAgent);
            return ResponseEntity.ok(ApiResponse.success("Memory compacted for agent: " + targetAgent, null));
        } catch (Exception e) {
            log.error("Error compacting memory", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Error compacting memory: " + e.getMessage()));
        }
    }

    @DeleteMapping("/memory")
    public ResponseEntity<ApiResponse<Void>> clearMemory(@RequestParam(required = false) String agentName) {
        try {
            if (memoryManager == null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Memory manager not available"));
            }

            String targetAgent = agentName != null ? agentName : agentManager.getCurrentAgentName();
            memoryManager.clearMemory(targetAgent);
            return ResponseEntity.ok(ApiResponse.success("Memory cleared for agent: " + targetAgent, null));
        } catch (Exception e) {
            log.error("Error clearing memory", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Error clearing memory: " + e.getMessage()));
        }
    }

    @GetMapping("/memory/size")
    public ResponseEntity<ApiResponse<Long>> getMemorySize(@RequestParam(required = false) String agentName) {
        try {
            if (memoryManager == null) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Memory manager not available"));
            }

            String targetAgent = agentName != null ? agentName : agentManager.getCurrentAgentName();
            long size = memoryManager.getMemorySize(targetAgent);
            return ResponseEntity.ok(ApiResponse.success(size));
        } catch (Exception e) {
            log.error("Error getting memory size", e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Error getting memory size: " + e.getMessage()));
        }
    }
}

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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;

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

    @Value("${spring.mvc.async.request-timeout:1800000}")
    private Long asyncRequestTimeout;

    @PostMapping
    public ResponseEntity<ApiResponse<ChatResponse>> sendMessage(@RequestBody ChatRequest request) {
        log.info("Received chat request: {}", request.getMessage());

        // Capture request parameters
        final String requestAgentName = (request.getAgentName() != null && !request.getAgentName().isEmpty())
            ? request.getAgentName()
            : agentManager.getCurrentAgentName();
        final String requestPanelId = (request.getPanelId() != null && !request.getPanelId().isEmpty())
            ? request.getPanelId()
            : "default-" + System.currentTimeMillis();

        log.info("🚀 POST request - Agent: {}, Panel: {}", requestAgentName, requestPanelId);

        try {
            // Set thread-local agent context for complete isolation
            orchestrator.setThreadAgentContext(requestAgentName, requestPanelId);

            long startTime = System.currentTimeMillis();

            // Process message with attachments - orchestrator will use thread-local context
            String response = orchestrator.processMessage(request.getMessage(), request.getAttachments());
            long responseTime = System.currentTimeMillis() - startTime;

            // Build response with agent name and panel ID
            ChatResponse chatResponse = ChatResponse.builder()
                    .response(response)
                    .agentName(requestAgentName)
                    .panelId(requestPanelId)
                    .modelUsed(configManager.getFallbackModel())
                    .timestamp(LocalDateTime.now())
                    .responseTimeMs(responseTime)
                    .build();

            log.info("✅ POST completed - Agent: {}, Panel: {}, Time: {}ms",
                    requestAgentName, requestPanelId, responseTime);

            return ResponseEntity.ok(ApiResponse.success(chatResponse));

        } catch (Exception e) {
            log.error("❌ POST error - Agent: {}, Panel: {}", requestAgentName, requestPanelId, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Error processing message: " + e.getMessage()));
        } finally {
            // CRITICAL: Always clear thread-local context to prevent memory leaks
            orchestrator.clearThreadAgentContext();
        }
    }

    /**
     * Send a message with file attachments (multipart form data)
     */
    @PostMapping(value = "/with-files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ChatResponse>> sendMessageWithFiles(
            @RequestParam String message,
            @RequestParam(required = false) MultipartFile[] files,
            @RequestParam(required = false) String agentName,
            @RequestParam(required = false) String panelId) {

        log.info("Received chat request with {} file(s): {}",
                 files != null ? files.length : 0,
                 message.substring(0, Math.min(50, message.length())));

        // Capture request parameters
        final String requestAgentName = (agentName != null && !agentName.isEmpty())
            ? agentName
            : agentManager.getCurrentAgentName();
        final String requestPanelId = (panelId != null && !panelId.isEmpty())
            ? panelId
            : "default-" + System.currentTimeMillis();

        log.info("🚀 Files request - Agent: {}, Panel: {}", requestAgentName, requestPanelId);

        // Process files
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

        try {
            // Set thread-local agent context for complete isolation
            orchestrator.setThreadAgentContext(requestAgentName, requestPanelId);

            long startTime = System.currentTimeMillis();

            // Process message with attachments - orchestrator will use thread-local context
            log.info("Processing message for agent '{}' with {} attachment(s)",
                    requestAgentName, attachments.size());

            String response;
            try {
                response = orchestrator.processMessage(message, attachments);
            } catch (Exception e) {
                log.error("Error processing message with attachments", e);
                throw new RuntimeException("Error processing message: " + e.getMessage(), e);
            }

            long responseTime = System.currentTimeMillis() - startTime;

            if (response == null || response.isEmpty()) {
                log.warn("Received empty response from orchestrator");
                return ResponseEntity.internalServerError()
                        .body(ApiResponse.error("Received empty response from AI model"));
            }

            log.info("✅ Files completed - Agent: {}, Panel: {}, Time: {}ms, Response: {} chars",
                    requestAgentName, requestPanelId, responseTime, response.length());

            // Build response with agent name and panel ID
            ChatResponse chatResponse = ChatResponse.builder()
                    .response(response)
                    .agentName(requestAgentName)
                    .panelId(requestPanelId)
                    .modelUsed(configManager.getFallbackModel())
                    .timestamp(LocalDateTime.now())
                    .responseTimeMs(responseTime)
                    .build();

            return ResponseEntity.ok(ApiResponse.success(chatResponse));

        } catch (Exception e) {
            log.error("❌ Files error - Agent: {}, Panel: {}", requestAgentName, requestPanelId, e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Error processing message: " + e.getMessage()));
        } finally {
            // CRITICAL: Always clear thread-local context to prevent memory leaks
            orchestrator.clearThreadAgentContext();
        }
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMessage(@RequestParam String message,
                                   @RequestParam(required = false) String agentName,
                                   @RequestParam(required = false) String panelId) {
        log.debug("SSE stream request for message: {}", message.substring(0, Math.min(50, message.length())));
        // Use configurable timeout from application.properties (default 30 minutes)
        SseEmitter emitter = new SseEmitter(asyncRequestTimeout);

        // Capture request parameters
        final String requestAgentName = (agentName != null && !agentName.isEmpty())
            ? agentName
            : agentManager.getCurrentAgentName();
        final String requestPanelId = (panelId != null && !panelId.isEmpty())
            ? panelId
            : "default-" + System.currentTimeMillis();

        log.info("🚀 Stream request - Agent: {}, Panel: {}", requestAgentName, requestPanelId);

        // Execute in separate thread - NO SYNCHRONIZATION for parallel processing
        executorService.execute(() -> {
            long startTime = System.currentTimeMillis();

            try {
                // Set thread-local agent context for complete isolation
                orchestrator.setThreadAgentContext(requestAgentName, requestPanelId);
                log.debug("Thread {} processing - Agent: {}, Panel: {}",
                         Thread.currentThread().getName(), requestAgentName, requestPanelId);

                // Send start event
                emitter.send(SseEmitter.event()
                        .name("start")
                        .data("Processing message..."));

                // Process message - orchestrator will use thread-local agent context
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

                // Get the last assistant message to check for attachments
                List<ConversationMessage> history = orchestrator.getConversationHistoryCopy();
                ConversationMessage lastMessage = null;
                if (!history.isEmpty()) {
                    // Find the last assistant message
                    for (int i = history.size() - 1; i >= 0; i--) {
                        if ("assistant".equals(history.get(i).getRole())) {
                            lastMessage = history.get(i);
                            break;
                        }
                    }
                }

                // Send complete event with JSON metadata including attachments
                String modelUsed = configManager.getFallbackModel();
                Map<String, Object> completeDataMap = new HashMap<>();
                completeDataMap.put("tokensUsed", 0);
                completeDataMap.put("processingTime", responseTime);
                completeDataMap.put("modelUsed", modelUsed != null ? modelUsed : "");
                completeDataMap.put("agentName", requestAgentName);
                completeDataMap.put("panelId", requestPanelId);

                // Add attachments if present
                if (lastMessage != null && lastMessage.getAttachments() != null && !lastMessage.getAttachments().isEmpty()) {
                    completeDataMap.put("attachments", lastMessage.getAttachments());
                    log.info("📎 Including {} attachment(s) in stream response", lastMessage.getAttachments().size());
                }

                // Configure ObjectMapper to handle Java 8 date/time types
                ObjectMapper mapper = new ObjectMapper();
                mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
                mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                String completeData = mapper.writeValueAsString(completeDataMap);
                emitter.send(SseEmitter.event()
                        .name("complete")
                        .data(completeData));

                emitter.complete();
                log.info("✅ Stream completed - Agent: {}, Panel: {}, Time: {}ms",
                        requestAgentName, requestPanelId, responseTime);

            } catch (Exception e) {
                log.error("❌ Stream error - Agent: {}, Panel: {}", requestAgentName, requestPanelId, e);
                try {
                    // Send error message as a chunk
                    emitter.send(SseEmitter.event()
                            .name("chunk")
                            .data("\n\n**Error:** " + e.getMessage()));

                    // CRITICAL: Always send complete event to finalize the message
                    long responseTime = System.currentTimeMillis() - startTime;
                    String completeData = String.format(
                        "{\"tokensUsed\":0,\"processingTime\":%d,\"error\":true,\"agentName\":\"%s\",\"panelId\":\"%s\"}",
                        responseTime,
                        requestAgentName,
                        requestPanelId
                    );
                    emitter.send(SseEmitter.event()
                            .name("complete")
                            .data(completeData));

                    // Complete normally (not with error) since we handled it gracefully
                    emitter.complete();
                } catch (Exception sendError) {
                    log.error("Error sending error for Agent: {}, Panel: {}", requestAgentName, requestPanelId, sendError);
                    // Last resort - complete with error
                    emitter.completeWithError(e);
                }
            } finally {
                // CRITICAL: Always clear thread-local context to prevent memory leaks
                orchestrator.clearThreadAgentContext();
            }
        });

        return emitter;
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<List<ConversationMessage>>> getHistory() {
        try {
            List<ConversationMessage> history = orchestrator.getConversationHistoryCopy();
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

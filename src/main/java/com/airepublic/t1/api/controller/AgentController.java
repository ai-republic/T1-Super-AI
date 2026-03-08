package com.airepublic.t1.api.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.airepublic.t1.agent.Agent;
import com.airepublic.t1.agent.AgentManager;
import com.airepublic.t1.agent.AgentOrchestrator;
import com.airepublic.t1.api.dto.AgentDetails;
import com.airepublic.t1.api.dto.AgentInfo;
import com.airepublic.t1.api.dto.AgentMessageRequest;
import com.airepublic.t1.api.dto.AgentMessageResponse;
import com.airepublic.t1.api.dto.CreateAgentRequest;
import com.airepublic.t1.api.dto.SwitchAgentRequest;
import com.airepublic.t1.api.dto.UpdateAgentRequest;
import com.airepublic.t1.config.AgentConfigService;
import com.airepublic.t1.config.AgentConfigurationManager;
import com.airepublic.t1.model.IndividualAgentConfig;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/agents")
@RequiredArgsConstructor
@Tag(name = "Agent Management", description = "APIs for managing AI agents - create, read, update, delete, and switch between agents")
public class AgentController {
    private final AgentManager agentManager;
    private final AgentOrchestrator orchestrator;
    private final AgentConfigService agentConfigService;
    private final AgentConfigurationManager configManager;

    @Operation(
            summary = "List all agents",
            description = "Retrieves a list of all available agents with their current status, conversation count, and metadata. The current active agent is marked with isCurrentAgent=true."
            )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Successfully retrieved agent list",
                    content = @Content(schema = @Schema(implementation = AgentInfo.class))
                    ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(schema = @Schema(implementation = com.airepublic.t1.api.dto.ApiResponse.class))
                    )
    })
    @GetMapping
    public ResponseEntity<com.airepublic.t1.api.dto.ApiResponse<List<AgentInfo>>> listAgents() {
        try {
            final String currentAgentName = agentManager.getCurrentAgentName();
            final List<AgentInfo> agents = agentManager.listAgents().stream()
                    .map(agent -> {
                        final IndividualAgentConfig config = agent.getConfig();
                        // Prefer status from config (CHARACTER.md), fallback to agent status
                        final String status = (config != null && config.getStatus() != null) ? config.getStatus() : agent.getStatus();
                        return AgentInfo.builder()
                                .name(agent.getName())
                                .role(config != null ? config.getRole() : null)
                                .purpose(config != null ? config.getPurpose() : null)
                                .status(status)
                                .createdAt(agent.getCreatedAt())
                                .lastActiveAt(agent.getLastActiveAt())
                                .conversationCount(agent.getConversationHistory().size())
                                .isCurrentAgent(agent.getName().equals(currentAgentName))
                                .build();
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(com.airepublic.t1.api.dto.ApiResponse.success(agents));

        } catch (final Exception e) {
            log.error("Error listing agents", e);
            return ResponseEntity.internalServerError()
                    .body(com.airepublic.t1.api.dto.ApiResponse.error("Error listing agents: " + e.getMessage()));
        }
    }

    @Operation(
            summary = "Create a new agent",
            description = """
                    Creates a new agent instance with full CHARACTER.md profile and configuration.

                    The agent will fork the current agent's conversation history and run in its own thread.
                    All provided fields will be used to create:
                    - Agent configuration (provider, model, context)
                    - CHARACTER.md file (role, purpose, personality, communication style, specialties, constraints)
                    - USAGE.md file (usage guide)

                    Only the 'name' field is required. All other fields are optional and will use defaults if not provided.
                    The agent is automatically persisted to disk at ~/.t1-super-ai/agents/{name}/
                    """
            )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Agent created successfully",
                    content = @Content(schema = @Schema(implementation = AgentInfo.class))
                    ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Bad request - invalid agent name or agent already exists",
                    content = @Content(schema = @Schema(implementation = com.airepublic.t1.api.dto.ApiResponse.class))
                    ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Internal server error",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
                    )
    })
    @PostMapping
    public ResponseEntity<com.airepublic.t1.api.dto.ApiResponse<AgentInfo>> createAgent(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = """
                            Agent creation request with full CHARACTER.md profile.
                            Required: name
                            Optional: role, purpose, personality, communicationStyle, specialties, constraints, context, provider, model
                            """,
                            required = true,
                            content = @Content(schema = @Schema(implementation = CreateAgentRequest.class))
                    )
            @RequestBody final CreateAgentRequest request) {
        try {
            if (request.getName() == null || request.getName().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(com.airepublic.t1.api.dto.ApiResponse.error("Agent name is required"));
            }

            if (agentManager.hasAgent(request.getName())) {
                return ResponseEntity.badRequest()
                        .body(com.airepublic.t1.api.dto.ApiResponse.error("Agent '" + request.getName() + "' already exists"));
            }

            // Get default values from config if not provided
            final com.airepublic.t1.model.AgentConfiguration globalConfig = configManager.getConfiguration();
            final com.airepublic.t1.model.AgentConfiguration.LLMProvider defaultProvider =
                    request.getProvider() != null ? request.getProvider() : globalConfig.getDefaultProvider();
            String defaultModel = request.getModel();
            if (defaultModel == null) {
                final var llmConfig = globalConfig.getLlmConfigs().get(defaultProvider);
                defaultModel = llmConfig != null ? llmConfig.getModel() : "default";
            }

            // Create comprehensive agent configuration
            final IndividualAgentConfig config = new IndividualAgentConfig(
                    request.getName(),                                                              // name
                    request.getRole() != null ? request.getRole() : "General Purpose Agent",       // role
                            request.getPurpose() != null ? request.getPurpose() : "A helpful AI assistant", // purpose
                                    request.getSpecialization() != null ? request.getSpecialization() : "General assistance", // specialization
                                            request.getCommunicationStyle() != null ? request.getCommunicationStyle() : "Clear and concise", // style
                                                    request.getPersonality() != null ? request.getPersonality() : "Professional and helpful", // personality
                                                            request.getEmojiPreference() != null ? request.getEmojiPreference() : "MODERATE", // emojiPreference
                                                                    request.getGuidelines() != null ? request.getGuidelines() : AgentConfigService.getCharacterGuidlinesTemplate(), // guidelines // guidelines
                                                                            "active",                                                                       // status
                                                                            java.time.LocalDateTime.now(),                                                  // createdAt
                                                                            java.time.LocalDateTime.now(),                                                  // lastModifiedAt
                                                                            defaultProvider,                                                                // provider
                                                                            defaultModel                                                                    // model
                    );

            // Create the agent
            final Agent agent = agentManager.createAgent(request.getName(), orchestrator, config);

            // Create agent folder
            agentConfigService.createAgentFolder(request.getName());

            // Create CHARACTER.md with full configuration
            agentConfigService.createCharacterMd(config, AgentConfigService.getCharacterGuidlinesTemplate());

            // Create USAGE.md
            agentConfigService.createUsageMd(request.getName(), config);

            final AgentInfo agentInfo = AgentInfo.builder()
                    .name(agent.getName())
                    .status(agent.getStatus())
                    .createdAt(agent.getCreatedAt())
                    .lastActiveAt(agent.getLastActiveAt())
                    .conversationCount(agent.getConversationHistory().size())
                    .isCurrentAgent(false)
                    .build();

            return ResponseEntity.ok(com.airepublic.t1.api.dto.ApiResponse.success(
                    "Agent '" + request.getName() + "' created successfully with CHARACTER.md profile", agentInfo));

        } catch (final Exception e) {
            log.error("Error creating agent", e);
            return ResponseEntity.internalServerError()
                    .body(com.airepublic.t1.api.dto.ApiResponse.error("Error creating agent: " + e.getMessage()));
        }
    }

    @Operation(
            summary = "Get agent details",
            description = "Retrieves detailed information about a specific agent including full CHARACTER.md profile"
            )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Agent found"),
            @ApiResponse(responseCode = "404", description = "Agent not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/{name}")
    public ResponseEntity<com.airepublic.t1.api.dto.ApiResponse<AgentDetails>> getAgent(
            @Parameter(description = "Name of the agent to retrieve", required = true)
            @PathVariable final String name) {
        try {
            if (!agentManager.hasAgent(name)) {
                return ResponseEntity.notFound().build();
            }

            final Agent agent = agentManager.getAgent(name);
            final String currentAgentName = agentManager.getCurrentAgentName();

            // Load full configuration from CHARACTER.md using AgentConfigService
            IndividualAgentConfig config = null;
            try {
                config = agentConfigService.loadIndividualAgentConfig(name);
                log.debug("Loaded CHARACTER.md config for agent: {}", name);
            } catch (final Exception e) {
                log.warn("Could not load CHARACTER.md for agent '{}', using in-memory config: {}", name, e.getMessage());
                // Fallback to in-memory config if CHARACTER.md is not available
                config = agent.getConfig();
            }

            // Build AgentDetails with all fields from config
            // Prefer status from config (CHARACTER.md), fallback to agent status
            final String status = (config != null && config.getStatus() != null) ? config.getStatus() : agent.getStatus();
            final AgentDetails agentDetails = AgentDetails.builder()
                    .name(agent.getName())
                    .status(status)
                    .createdAt(agent.getCreatedAt())
                    .lastActiveAt(agent.getLastActiveAt())
                    .conversationCount(agent.getConversationHistory().size())
                    .isCurrentAgent(agent.getName().equals(currentAgentName))
                    .role(config != null ? config.getRole() : null)
                    .purpose(config != null ? config.getPurpose() : null)
                    .specialization(config != null ? config.getSpecialization() : null)
                    .provider(config != null ? config.getProvider() : null)
                    .model(config != null ? config.getModel() : null)
                    .style(config != null ? config.getStyle() : null)
                    .personality(config != null ? config.getPersonality() : null)
                    .emojiPreference(config != null ? config.getEmojiPreference() : null)
                    .guidelines(config != null ? config.getGuidelines() : null)
                    .build();

            return ResponseEntity.ok(com.airepublic.t1.api.dto.ApiResponse.success(agentDetails));

        } catch (final Exception e) {
            log.error("Error retrieving agent", e);
            return ResponseEntity.internalServerError()
                    .body(com.airepublic.t1.api.dto.ApiResponse.error("Error retrieving agent: " + e.getMessage()));
        }
    }

    @Operation(
            summary = "Delete an agent",
            description = "Removes an agent from the system. The master agent cannot be deleted. If the deleted agent is currently active, the system switches back to the master agent."
            )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Agent deleted successfully"),
            @ApiResponse(responseCode = "400", description = "Bad request - cannot delete master agent"),
            @ApiResponse(responseCode = "404", description = "Agent not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @DeleteMapping("/{name}")
    public ResponseEntity<com.airepublic.t1.api.dto.ApiResponse<Void>> removeAgent(
            @Parameter(description = "Name of the agent to delete", required = true)
            @PathVariable final String name) {
        try {
            if (!agentManager.hasAgent(name)) {
                return ResponseEntity.notFound().build();
            }

            final boolean removed = agentManager.removeAgent(name);

            if (removed) {
                return ResponseEntity.ok(com.airepublic.t1.api.dto.ApiResponse.success(
                        "Agent '" + name + "' removed successfully", null));
            } else {
                return ResponseEntity.badRequest()
                        .body(com.airepublic.t1.api.dto.ApiResponse.error("Failed to remove agent"));
            }

        } catch (final IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(com.airepublic.t1.api.dto.ApiResponse.error(e.getMessage()));
        } catch (final Exception e) {
            log.error("Error removing agent", e);
            return ResponseEntity.internalServerError()
                    .body(com.airepublic.t1.api.dto.ApiResponse.error("Error removing agent: " + e.getMessage()));
        }
    }

    @Operation(
            summary = "Switch current agent",
            description = "Changes the current active agent. All subsequent operations will use this agent's context until switched again."
            )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully switched to the agent"),
            @ApiResponse(responseCode = "400", description = "Bad request - invalid agent name"),
            @ApiResponse(responseCode = "404", description = "Agent not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PutMapping("/current")
    public ResponseEntity<com.airepublic.t1.api.dto.ApiResponse<Void>> switchAgent(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Request containing the agent name to switch to",
                    required = true,
                    content = @Content(schema = @Schema(implementation = SwitchAgentRequest.class))
                    )
            @RequestBody final SwitchAgentRequest request) {
        try {
            if (request.getAgentName() == null || request.getAgentName().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(com.airepublic.t1.api.dto.ApiResponse.error("Agent name is required"));
            }

            if (!agentManager.hasAgent(request.getAgentName())) {
                return ResponseEntity.notFound().build();
            }

            if (agentManager.getCurrentAgentName().equals(request.getAgentName())) {
                return ResponseEntity.ok(com.airepublic.t1.api.dto.ApiResponse.success(
                        "Already using agent: " + request.getAgentName(), null));
            }

            agentManager.switchToAgent(request.getAgentName());

            return ResponseEntity.ok(com.airepublic.t1.api.dto.ApiResponse.success(
                    "Switched to agent: " + request.getAgentName(), null));

        } catch (final Exception e) {
            log.error("Error switching agent", e);
            return ResponseEntity.internalServerError()
                    .body(com.airepublic.t1.api.dto.ApiResponse.error("Error switching agent: " + e.getMessage()));
        }
    }

    @Operation(
            summary = "Get current agent",
            description = "Retrieves information about the currently active agent"
            )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Successfully retrieved current agent"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @GetMapping("/current")
    public ResponseEntity<com.airepublic.t1.api.dto.ApiResponse<AgentInfo>> getCurrentAgent() {
        try {
            final String currentAgentName = agentManager.getCurrentAgentName();
            final Optional<Agent> agentOpt = agentManager.listAgents().stream()
                    .filter(a -> a.getName().equals(currentAgentName))
                    .findFirst();

            // If no current agent is set or found, return 204 No Content
            if (agentOpt.isEmpty()) {
                log.info("No current agent selected");
                return ResponseEntity.status(204).build();
            }

            final Agent agent = agentOpt.get();
            final IndividualAgentConfig config = agent.getConfig();
            // Prefer status from config (CHARACTER.md), fallback to agent status
            final String status = (config != null && config.getStatus() != null) ? config.getStatus() : agent.getStatus();
            final AgentInfo agentInfo = AgentInfo.builder()
                    .name(agent.getName())
                    .status(status)
                    .createdAt(agent.getCreatedAt())
                    .lastActiveAt(agent.getLastActiveAt())
                    .conversationCount(agent.getConversationHistory().size())
                    .isCurrentAgent(true)
                    .build();

            return ResponseEntity.ok(com.airepublic.t1.api.dto.ApiResponse.success(agentInfo));

        } catch (final Exception e) {
            log.error("Error retrieving current agent", e);
            return ResponseEntity.internalServerError()
                    .body(com.airepublic.t1.api.dto.ApiResponse.error("Error retrieving current agent: " + e.getMessage()));
        }
    }

    @Operation(
            summary = "Update agent configuration",
            description = "Updates an agent's configuration including role, context, LLM provider, and model. All fields are optional - only provided fields will be updated. Changes are persisted to disk."
            )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Agent configuration updated successfully"),
            @ApiResponse(responseCode = "404", description = "Agent not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PutMapping("/{name}")
    public ResponseEntity<com.airepublic.t1.api.dto.ApiResponse<AgentInfo>> updateAgent(
            @Parameter(description = "Name of the agent to update", required = true)
            @PathVariable final String name,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Agent configuration update request. All fields are optional.",
                    required = true,
                    content = @Content(schema = @Schema(implementation = UpdateAgentRequest.class))
                    )
            @RequestBody final UpdateAgentRequest request) {
        try {
            if (!agentManager.hasAgent(name)) {
                return ResponseEntity.notFound().build();
            }

            final Agent agent = agentManager.getAgent(name);

            // Load config from CHARACTER.md to get latest values
            IndividualAgentConfig config = null;
            try {
                config = agentConfigService.loadIndividualAgentConfig(name);
                log.debug("Loaded CHARACTER.md config for update: {}", name);
            } catch (final Exception e) {
                log.warn("Could not load CHARACTER.md for agent '{}', using in-memory config: {}", name, e.getMessage());
                // Fallback to in-memory config
                config = agent.getConfig();
            }

            // If no config exists, create one
            if (config == null) {
                config = new IndividualAgentConfig();
                config.setName(name);
            }

            // Update fields if provided
            if (request.getRole() != null) {
                config.setRole(request.getRole());
            }
            if (request.getPurpose() != null) {
                config.setPurpose(request.getPurpose());
            }
            if (request.getSpecialization() != null) {
                config.setSpecialization(request.getSpecialization());
            }
            if (request.getPersonality() != null) {
                config.setPersonality(request.getPersonality());
            }
            if (request.getStyle() != null) {
                config.setStyle(request.getStyle());
            }
            if (request.getEmojiPreference() != null) {
                config.setEmojiPreference(request.getEmojiPreference());
            }
            if (request.getGuidelines() != null) {
                config.setGuidelines(request.getGuidelines());
            }
            if (request.getProvider() != null) {
                config.setProvider(request.getProvider());
            }
            if (request.getModel() != null) {
                config.setModel(request.getModel());
            }

            // Update timestamp
            config.updateLastModified();

            // Update the agent's configuration in memory
            final Agent updatedAgent = agentManager.updateAgentConfig(name, config);

            // Persist to disk (updates CHARACTER.md)
            agentConfigService.updateCharacterMd(config);

            // Build response
            final String currentAgentName = agentManager.getCurrentAgentName();
            final AgentInfo agentInfo = AgentInfo.builder()
                    .name(updatedAgent.getName())
                    .status(updatedAgent.getStatus())
                    .createdAt(updatedAgent.getCreatedAt())
                    .lastActiveAt(updatedAgent.getLastActiveAt())
                    .conversationCount(updatedAgent.getConversationHistory().size())
                    .isCurrentAgent(updatedAgent.getName().equals(currentAgentName))
                    .build();

            return ResponseEntity.ok(com.airepublic.t1.api.dto.ApiResponse.success(
                    "Agent '" + name + "' configuration updated successfully", agentInfo));

        } catch (final Exception e) {
            log.error("Error updating agent", e);
            return ResponseEntity.internalServerError()
                    .body(com.airepublic.t1.api.dto.ApiResponse.error("Error updating agent: " + e.getMessage()));
        }
    }

    @Operation(
            summary = "Send message to agent",
            description = "Sends a message to a specific agent and retrieves the response. The agent processes the message using its configured LLM provider and returns the response with metadata including response time and model used."
            )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Message processed successfully"),
            @ApiResponse(responseCode = "400", description = "Bad request - message is required"),
            @ApiResponse(responseCode = "404", description = "Agent not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @Tag(name = "Agent Messaging")
    @PostMapping("/{name}/message")
    public ResponseEntity<com.airepublic.t1.api.dto.ApiResponse<AgentMessageResponse>> sendMessageToAgent(
            @Parameter(description = "Name of the agent to send message to", required = true)
            @PathVariable final String name,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Message request containing the prompt to send to the agent",
                    required = true,
                    content = @Content(schema = @Schema(implementation = AgentMessageRequest.class))
                    )
            @RequestBody final AgentMessageRequest request) {
        try {
            if (!agentManager.hasAgent(name)) {
                return ResponseEntity.notFound().build();
            }

            if (request.getMessage() == null || request.getMessage().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(com.airepublic.t1.api.dto.ApiResponse.error("Message is required"));
            }

            // Get the agent
            final Agent agent = agentManager.getAgent(name);

            // Save current agent and switch to target agent
            final String previousAgent = agentManager.getCurrentAgentName();
            final boolean needToSwitch = !previousAgent.equals(name);

            if (needToSwitch) {
                agentManager.switchToAgent(name);
            }

            // Process message through orchestrator
            final long startTime = System.currentTimeMillis();
            final String response = orchestrator.processMessage(request.getMessage());
            final long endTime = System.currentTimeMillis();

            // Switch back if needed
            if (needToSwitch) {
                agentManager.switchToAgent(previousAgent);
            }

            // Build response
            final AgentMessageResponse messageResponse = AgentMessageResponse.builder()
                    .response(response)
                    .agentName(name)
                    .modelUsed(agent.getConfig() != null ?
                            agent.getConfig().getProvider() + "/" + agent.getConfig().getModel() :
                            "default")
                    .timestamp(LocalDateTime.now())
                    .responseTimeMs(endTime - startTime)
                    .build();

            return ResponseEntity.ok(com.airepublic.t1.api.dto.ApiResponse.success(messageResponse));

        } catch (final Exception e) {
            log.error("Error processing message for agent: {}", name, e);
            return ResponseEntity.internalServerError()
                    .body(com.airepublic.t1.api.dto.ApiResponse.error("Error processing message: " + e.getMessage()));
        }
    }
}

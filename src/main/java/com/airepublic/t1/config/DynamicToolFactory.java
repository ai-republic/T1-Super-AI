package com.airepublic.t1.config;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.airepublic.t1.mcp.MCPClient;
import com.airepublic.t1.tools.AgentTool;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Factory for creating tools dynamically based on configuration.
 * Supports various tool types: Java classes, MCP tools, scripts, HTTP APIs, etc.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DynamicToolFactory {
    private final MCPClient mcpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Create a tool instance from configuration
     */
    public AgentTool createTool(final ToolConfiguration config) {
        if (!config.isEnabled()) {
            log.debug("Tool {} is disabled, skipping creation", config.getName());
            return null;
        }

        try {
            return switch (config.getType()) {
                case ToolConfiguration.ToolType.JAVA -> createJavaClassTool(config);
                case ToolConfiguration.ToolType.MCP_TOOL -> createMcpTool(config);
                case ToolConfiguration.ToolType.SCRIPT -> createScriptTool(config);
                case ToolConfiguration.ToolType.HTTP_API -> createHttpApiTool(config);
                case ToolConfiguration.ToolType.CUSTOM -> createCustomTool(config);
            };
        } catch (final Exception e) {
            log.error("Error creating tool from configuration: {}", config.getName(), e);
            return null;
        }
    }

    private AgentTool createJavaClassTool(final ToolConfiguration config) throws Exception {
        final Class<?> toolClass = Class.forName(config.getClassName());

        if (!AgentTool.class.isAssignableFrom(toolClass)) {
            throw new IllegalArgumentException("Class " + config.getClassName() + " does not implement AgentTool");
        }

        @SuppressWarnings("unchecked")
        final
        Class<? extends AgentTool> agentToolClass = (Class<? extends AgentTool>) toolClass;
        final AgentTool tool = agentToolClass.getDeclaredConstructor().newInstance();

        log.info("Created Java class tool: {}", config.getName());
        return tool;
    }

    private AgentTool createMcpTool(final ToolConfiguration config) {
        // Create a wrapper tool that delegates to an MCP server tool
        return new AgentTool() {
            @Override
            public String getName() {
                return config.getName();
            }

            @Override
            public String getDescription() {
                return config.getDescription();
            }

            @Override
            public Map<String, Object> getParameterSchema() {
                return config.getInputSchema() != null ? config.getInputSchema() : new HashMap<>();
            }

            @Override
            public String execute(final Map<String, Object> parameters) throws Exception {
                final String serverName = config.getMcpServerName();
                final String toolName = config.getMcpToolName() != null ? config.getMcpToolName() : config.getName();

                // Delegate to MCP client to execute the tool
                return mcpClient.callTool(serverName, toolName, parameters);
            }
        };
    }

    private AgentTool createScriptTool(final ToolConfiguration config) {
        return new AgentTool() {
            @Override
            public String getName() {
                return config.getName();
            }

            @Override
            public String getDescription() {
                return config.getDescription();
            }

            @Override
            public Map<String, Object> getParameterSchema() {
                return config.getInputSchema() != null ? config.getInputSchema() : new HashMap<>();
            }

            @Override
            public String execute(final Map<String, Object> parameters) throws Exception {
                String script = config.getExecutionScript();
                final String language = config.getScriptLanguage();

                // Replace parameter placeholders in script
                for (final Map.Entry<String, Object> entry : parameters.entrySet()) {
                    final String placeholder = "${" + entry.getKey() + "}";
                    script = script.replace(placeholder, String.valueOf(entry.getValue()));
                }

                // Execute the script
                ProcessBuilder processBuilder;
                if ("bash".equalsIgnoreCase(language) || "sh".equalsIgnoreCase(language)) {
                    processBuilder = new ProcessBuilder("bash", "-c", script);
                } else if ("python".equalsIgnoreCase(language)) {
                    processBuilder = new ProcessBuilder("python", "-c", script);
                } else {
                    throw new IllegalArgumentException("Unsupported script language: " + language);
                }

                final Process process = processBuilder.start();

                final StringBuilder output = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                }

                final int exitCode = process.waitFor();
                if (exitCode != 0) {
                    final StringBuilder errorOutput = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            errorOutput.append(line).append("\n");
                        }
                    }
                    throw new RuntimeException("Script execution failed with exit code " + exitCode + ": " + errorOutput);
                }

                return output.toString();
            }
        };
    }

    private AgentTool createHttpApiTool(final ToolConfiguration config) {
        return new AgentTool() {
            @Override
            public String getName() {
                return config.getName();
            }

            @Override
            public String getDescription() {
                return config.getDescription();
            }

            @Override
            public Map<String, Object> getParameterSchema() {
                return config.getInputSchema() != null ? config.getInputSchema() : new HashMap<>();
            }

            @Override
            public String execute(final Map<String, Object> parameters) throws Exception {
                // HTTP API tool implementation
                final String url = (String) config.getParameters().get("url");
                final String method = (String) config.getParameters().getOrDefault("method", "GET");

                // Build URL with parameters
                final StringBuilder urlBuilder = new StringBuilder(url);
                if ("GET".equalsIgnoreCase(method)) {
                    urlBuilder.append("?");
                    parameters.forEach((key, value) ->
                    urlBuilder.append(key).append("=").append(value).append("&")
                            );
                }

                final ProcessBuilder processBuilder = new ProcessBuilder(
                        "curl", "-X", method, urlBuilder.toString()
                        );

                final Process process = processBuilder.start();
                final StringBuilder output = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                }

                return output.toString();
            }
        };
    }

    private AgentTool createCustomTool(final ToolConfiguration config) {
        // Custom tool implementation - can be extended based on specific needs
        log.warn("Custom tool type not fully implemented: {}", config.getName());
        return null;
    }
}

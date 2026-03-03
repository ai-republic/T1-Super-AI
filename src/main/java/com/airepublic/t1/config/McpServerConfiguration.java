package com.airepublic.t1.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.Map;

/**
 * Configuration model for external MCP servers.
 * MCP servers can be configured via JSON files in .t1-super-ai/mcp-servers/
 */
@Data
public class McpServerConfiguration {
    @JsonProperty("name")
    private String name;

    @JsonProperty("description")
    private String description;

    @JsonProperty("enabled")
    private boolean enabled = true;

    @JsonProperty("transport")
    private TransportType transport;

    @JsonProperty("command")
    private String command;

    @JsonProperty("args")
    private String[] args;

    @JsonProperty("env")
    private Map<String, String> env;

    @JsonProperty("url")
    private String url;

    @JsonProperty("autoConnect")
    private boolean autoConnect = true;

    @JsonProperty("reconnectOnFailure")
    private boolean reconnectOnFailure = true;

    @JsonProperty("timeout")
    private int timeout = 30000;

    public enum TransportType {
        STDIO,           // Standard input/output
        HTTP,            // HTTP transport
        SSE              // Server-sent events
    }
}

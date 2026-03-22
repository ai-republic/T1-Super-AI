package com.airepublic.t1.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Unified tool executor that handles all 5 plugin types according to USAGE.md:
 * 1. JAVA (JAR) - Execute Java JAR files
 * 2. JAVA (Maven) - Execute Maven-built JAR files
 * 3. SCRIPT (JavaScript) - Execute Node.js scripts
 * 4. SCRIPT (Python) - Execute Python scripts
 * 5. HTTP_API - Make HTTP API calls
 *
 * Maintains MCP specification 2024-11-05 compatibility for tool execution.
 */
@Slf4j
public class PluginTool implements AgentTool {
    private final String name;
    private final String description;
    private final String pluginType; // JAVA, SCRIPT, HTTP_API
    private final Path pluginDir;
    private final JsonNode toolConfig;
    private final Map<String, Object> parameterSchema;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Timeout for execution (30 seconds)
    private static final long EXECUTION_TIMEOUT_SECONDS = 30;

    public PluginTool(String name, String description, String pluginType, Path pluginDir,
                     JsonNode toolConfig, Map<String, Object> parameterSchema) {
        this.name = name;
        this.description = description;
        this.pluginType = pluginType;
        this.pluginDir = pluginDir;
        this.toolConfig = toolConfig;
        this.parameterSchema = parameterSchema;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        return parameterSchema;
    }

    @Override
    public String execute(Map<String, Object> arguments) throws Exception {
        log.debug("Executing {} tool: {} with arguments: {}", pluginType, name, arguments);

        return switch (pluginType) {
            case "JAVA" -> executeJavaTool(arguments);
            case "SCRIPT" -> executeScriptTool(arguments);
            case "HTTP_API" -> executeHttpApiTool(arguments);
            default -> throw new Exception("Unsupported plugin type: " + pluginType);
        };
    }

    /**
     * Execute JAVA plugin tool (JAR file)
     * Format: java -jar <jarPath> -D<prop=value> <arg1> <arg2>
     * Or: java -cp <jarPath> <executable> -D<prop=value> <arg1> <arg2>
     */
    private String executeJavaTool(Map<String, Object> arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("java");

        // Get JAR path from plugin directory
        String jarPath = getJarPath();
        Path jarFile = pluginDir.resolve(jarPath);

        if (!Files.exists(jarFile)) {
            throw new Exception("JAR file not found: " + jarFile);
        }

        // Check if executable is specified (for multi-class JARs)
        String executable = toolConfig.has("executable") ? toolConfig.get("executable").asText() : null;

        if (executable != null) {
            command.add("-cp");
            command.add(jarFile.toString());
            command.add(executable);
        } else {
            command.add("-jar");
            command.add(jarFile.toString());
        }

        // Add system properties from inputSchema->properties
        JsonNode inputSchema = toolConfig.get("inputSchema");
        if (inputSchema != null && inputSchema.has("properties")) {
            JsonNode properties = inputSchema.get("properties");
            properties.fieldNames().forEachRemaining(propName -> {
                if (arguments.containsKey(propName)) {
                    command.add("-D" + propName + "=" + arguments.get(propName));
                }
            });
        }

        // Add arguments from inputSchema->arguments
        if (inputSchema != null && inputSchema.has("arguments")) {
            JsonNode argumentsDef = inputSchema.get("arguments");
            argumentsDef.fieldNames().forEachRemaining(argName -> {
                if (arguments.containsKey(argName)) {
                    command.add("--" + argName + "=" + arguments.get(argName));
                }
            });
        }

        return executeCommand(command);
    }

    /**
     * Execute SCRIPT plugin tool (Python, JavaScript, Bash)
     * Format: <interpreter> <executable> <arg1> <arg2>
     */
    private String executeScriptTool(Map<String, Object> arguments) throws Exception {
        String executable = toolConfig.get("executable").asText();
        Path executablePath = pluginDir.resolve(executable);

        if (!Files.exists(executablePath)) {
            throw new Exception("Script not found: " + executablePath);
        }

        List<String> command = new ArrayList<>();

        // Determine interpreter based on file extension
        String ext = getFileExtension(executable);
        switch (ext) {
            case "py" -> command.add("python");
            case "js" -> command.add("node");
            case "sh" -> command.add("bash");
            default -> throw new Exception("Unsupported script type: " + ext);
        }

        command.add(executablePath.toString());

        // Set environment variables from inputSchema->properties
        Map<String, String> envVars = new HashMap<>();
        JsonNode inputSchema = toolConfig.get("inputSchema");
        if (inputSchema != null && inputSchema.has("properties")) {
            JsonNode properties = inputSchema.get("properties");
            properties.fieldNames().forEachRemaining(propName -> {
                if (arguments.containsKey(propName)) {
                    envVars.put(propName, String.valueOf(arguments.get(propName)));
                }
            });
        }

        // Add arguments from inputSchema->arguments
        if (inputSchema != null && inputSchema.has("arguments")) {
            JsonNode argumentsDef = inputSchema.get("arguments");
            argumentsDef.fieldNames().forEachRemaining(argName -> {
                if (arguments.containsKey(argName)) {
                    command.add("--" + argName + "=" + arguments.get(argName));
                }
            });
        }

        return executeCommand(command, envVars);
    }

    /**
     * Execute HTTP_API plugin tool
     * Format: curl -X <method> <url> -H <header> -d <body>
     */
    private String executeHttpApiTool(Map<String, Object> arguments) throws Exception {
        String urlString = toolConfig.get("url").asText();
        String method = toolConfig.get("method").asText();

        // Replace environment variables in URL
        for (Map.Entry<String, Object> entry : arguments.entrySet()) {
            urlString = urlString.replace("$" + entry.getKey(), String.valueOf(entry.getValue()));
        }

        // Add parameters to URL
        JsonNode inputSchema = toolConfig.get("inputSchema");
        if (inputSchema != null && inputSchema.has("parameters")) {
            List<String> params = new ArrayList<>();
            JsonNode parametersDef = inputSchema.get("parameters");
            parametersDef.fieldNames().forEachRemaining(paramName -> {
                if (arguments.containsKey(paramName)) {
                    params.add(paramName + "=" + arguments.get(paramName));
                }
            });
            if (!params.isEmpty()) {
                urlString += "?" + String.join("&", params);
            }
        }

        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);

        // Add headers
        if (toolConfig.has("headers")) {
            JsonNode headers = toolConfig.get("headers");
            headers.fieldNames().forEachRemaining(headerName -> {
                String headerValue = headers.get(headerName).asText();
                // Replace environment variables in headers
                for (Map.Entry<String, Object> entry : arguments.entrySet()) {
                    headerValue = headerValue.replace("$" + entry.getKey(), String.valueOf(entry.getValue()));
                }
                conn.setRequestProperty(headerName, headerValue);
            });
        }

        // Add body for POST/PUT/PATCH
        if (List.of("POST", "PUT", "PATCH").contains(method) && inputSchema.has("body")) {
            conn.setDoOutput(true);
            String body = inputSchema.get("body").asText();
            // Replace variables in body
            for (Map.Entry<String, Object> entry : arguments.entrySet()) {
                body = body.replace("$" + entry.getKey(), String.valueOf(entry.getValue()));
            }
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes());
                os.flush();
            }
        }

        // Read response
        int responseCode = conn.getResponseCode();
        StringBuilder response = new StringBuilder();

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line).append("\n");
            }
        }

        if (responseCode >= 400) {
            throw new Exception("HTTP " + responseCode + ": " + response.toString());
        }

        // Save to response file if specified
        if (inputSchema.has("responseFile") && arguments.containsKey("responseFile")) {
            String responseFile = String.valueOf(arguments.get("responseFile"));
            Files.writeString(Paths.get(responseFile), response.toString());
        }

        return response.toString();
    }

    /**
     * Execute command with environment variables
     */
    private String executeCommand(List<String> command, Map<String, String> envVars) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(pluginDir.toFile());
        processBuilder.redirectErrorStream(true);

        // Add environment variables
        if (envVars != null && !envVars.isEmpty()) {
            processBuilder.environment().putAll(envVars);
        }

        log.debug("Executing command: {}", String.join(" ", command));
        Process process = processBuilder.start();

        // Read output
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        // Wait for process with timeout
        boolean finished = process.waitFor(EXECUTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        if (!finished) {
            process.destroyForcibly();
            throw new Exception("Execution timed out after " + EXECUTION_TIMEOUT_SECONDS + " seconds");
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            String errorMessage = "Execution failed with exit code " + exitCode + ":\n" + output.toString();
            log.error(errorMessage);
            throw new Exception(errorMessage);
        }

        return output.toString().trim();
    }

    /**
     * Execute command without environment variables
     */
    private String executeCommand(List<String> command) throws Exception {
        return executeCommand(command, null);
    }

    /**
     * Get JAR path from plugin.json (from parent plugin config)
     */
    private String getJarPath() throws Exception {
        // JAR path should be in the parent plugin.json, not tool config
        // We need to read it from the plugin directory
        Path pluginJsonPath = pluginDir.resolve("plugin.json");
        if (!Files.exists(pluginJsonPath)) {
            throw new Exception("plugin.json not found in " + pluginDir);
        }

        JsonNode pluginConfig = objectMapper.readTree(pluginJsonPath.toFile());
        if (!pluginConfig.has("jarPath")) {
            throw new Exception("jarPath not specified in plugin.json");
        }

        return pluginConfig.get("jarPath").asText();
    }

    /**
     * Get file extension
     */
    private String getFileExtension(String filePath) {
        int lastDotIndex = filePath.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < filePath.length() - 1) {
            return filePath.substring(lastDotIndex + 1).toLowerCase();
        }
        return "";
    }
}

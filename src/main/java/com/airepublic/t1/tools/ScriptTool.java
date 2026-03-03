package com.airepublic.t1.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Script-based tool that executes external scripts (Python, Node.js, etc.)
 * and returns the output.
 *
 * Arguments are passed to the script as JSON via stdin.
 */
@Slf4j
public class ScriptTool implements AgentTool {
    private final String name;
    private final String description;
    private final String executablePath;
    private final Map<String, Object> parameterSchema;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Timeout for script execution (30 seconds)
    private static final long EXECUTION_TIMEOUT_SECONDS = 30;

    public ScriptTool(String name, String description, String executablePath, Map<String, Object> parameterSchema) {
        this.name = name;
        this.description = description;
        this.executablePath = executablePath;
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
        log.debug("Executing script tool: {} with arguments: {}", name, arguments);

        // Determine the interpreter based on file extension
        List<String> command = new ArrayList<>();
        String ext = getFileExtension(executablePath);

        switch (ext) {
            case "py":
                command.add("python");
                break;
            case "js":
                command.add("node");
                break;
            case "sh":
                command.add("bash");
                break;
            case "bat":
                // On Windows, .bat files can be executed directly
                break;
            default:
                // Try to execute directly (assumes executable has shebang)
                break;
        }

        command.add(executablePath);

        // Convert arguments to JSON string
        String argumentsJson = objectMapper.writeValueAsString(arguments);

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(new File(executablePath).getParentFile());

        // Redirect error stream to output stream
        processBuilder.redirectErrorStream(true);

        log.debug("Starting process: {}", command);
        Process process = processBuilder.start();

        // Write arguments to stdin
        try (var outputStream = process.getOutputStream()) {
            outputStream.write(argumentsJson.getBytes());
            outputStream.flush();
        }

        // Read output
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        // Wait for process to complete with timeout
        boolean finished = process.waitFor(EXECUTION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        if (!finished) {
            process.destroyForcibly();
            throw new Exception("Script execution timed out after " + EXECUTION_TIMEOUT_SECONDS + " seconds");
        }

        int exitCode = process.exitValue();

        if (exitCode != 0) {
            String errorMessage = "Script execution failed with exit code " + exitCode + ":\n" + output.toString();
            log.error(errorMessage);
            throw new Exception(errorMessage);
        }

        String result = output.toString().trim();
        log.debug("Script tool {} executed successfully. Output length: {}", name, result.length());

        return result;
    }

    private String getFileExtension(String filePath) {
        int lastDotIndex = filePath.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < filePath.length() - 1) {
            return filePath.substring(lastDotIndex + 1).toLowerCase();
        }
        return "";
    }
}

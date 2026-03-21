package com.airepublic.t1.tools;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class CmdTool implements AgentTool {

    @Override
    public String getName() {
        return "cmd";
    }

    @Override
    public String getDescription() {
        return "Executes a cmd command and returns the output. Use for system operations on Windows, git commands, npm, etc.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        final Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");

        final Map<String, Object> properties = new HashMap<>();

        final Map<String, Object> commandProp = new HashMap<>();
        commandProp.put("type", "string");
        commandProp.put("description", "The bash command to execute");
        properties.put("command", commandProp);

        final Map<String, Object> timeoutProp = new HashMap<>();
        timeoutProp.put("type", "integer");
        timeoutProp.put("description", "Timeout in seconds (default: 30)");
        properties.put("timeout", timeoutProp);

        schema.put("properties", properties);
        schema.put("required", new String[]{"command"});

        return schema;
    }

    @Override
    public String execute(final Map<String, Object> arguments) throws Exception {
        final String command = (String) arguments.get("command");
        final Integer timeout = arguments.containsKey("timeout")
                ? ((Number) arguments.get("timeout")).intValue()
                        : 30;

        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("command is required");
        }

        log.info("Executing command: {}", command);

        ProcessBuilder processBuilder;
        final String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) {
            processBuilder = new ProcessBuilder("cmd.exe", "/c", command);
        } else {
            processBuilder = new ProcessBuilder("bash", "-c", command);
        }

        processBuilder.redirectErrorStream(true);
        final Process process = processBuilder.start();

        final StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        final boolean finished = process.waitFor(timeout, TimeUnit.SECONDS);

        if (!finished) {
            process.destroyForcibly();
            return "Error: Command timed out after " + timeout + " seconds\n" + output.toString();
        }

        final int exitCode = process.exitValue();
        final String result = output.toString();

        log.info("Command completed with exit code: {}", exitCode);

        if (exitCode != 0) {
            return "Command failed with exit code " + exitCode + ":\n" + result;
        }

        return result;
    }
}

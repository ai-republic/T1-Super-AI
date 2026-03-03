package com.airepublic.t1.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class ReadFileTool implements AgentTool {

    @Override
    public String getName() {
        return "read_file";
    }

    @Override
    public String getDescription() {
        return "Reads the contents of a file from the filesystem. Returns the file content as a string.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new HashMap<>();
        Map<String, Object> filePathProp = new HashMap<>();
        filePathProp.put("type", "string");
        filePathProp.put("description", "The path to the file to read");
        properties.put("file_path", filePathProp);

        schema.put("properties", properties);
        schema.put("required", new String[]{"file_path"});

        return schema;
    }

    @Override
    public String execute(Map<String, Object> arguments) throws Exception {
        String filePath = (String) arguments.get("file_path");
        if (filePath == null || filePath.isEmpty()) {
            throw new IllegalArgumentException("file_path is required");
        }

        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            return "Error: File not found: " + filePath;
        }

        if (Files.isDirectory(path)) {
            return "Error: Path is a directory, not a file: " + filePath;
        }

        String content = Files.readString(path);
        log.info("Read file: {} ({} bytes)", filePath, content.length());

        return content;
    }
}

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
public class WriteFileTool implements AgentTool {

    @Override
    public String getName() {
        return "write_file";
    }

    @Override
    public String getDescription() {
        return "Writes content to a file. Creates the file if it doesn't exist, overwrites if it does.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> filePathProp = new HashMap<>();
        filePathProp.put("type", "string");
        filePathProp.put("description", "The path to the file to write");
        properties.put("file_path", filePathProp);

        Map<String, Object> contentProp = new HashMap<>();
        contentProp.put("type", "string");
        contentProp.put("description", "The content to write to the file");
        properties.put("content", contentProp);

        schema.put("properties", properties);
        schema.put("required", new String[]{"file_path", "content"});

        return schema;
    }

    @Override
    public String execute(Map<String, Object> arguments) throws Exception {
        String filePath = (String) arguments.get("file_path");
        String content = (String) arguments.get("content");

        if (filePath == null || filePath.isEmpty()) {
            throw new IllegalArgumentException("file_path is required");
        }
        if (content == null) {
            content = "";
        }

        Path path = Paths.get(filePath);

        // Create parent directories if they don't exist
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }

        Files.writeString(path, content);
        log.info("Wrote file: {} ({} bytes)", filePath, content.length());

        return "Successfully wrote " + content.length() + " bytes to " + filePath;
    }
}

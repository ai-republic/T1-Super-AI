package com.airepublic.t1.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Component
public class ListDirectoryTool implements AgentTool {

    @Override
    public String getName() {
        return "list_directory";
    }

    @Override
    public String getDescription() {
        return "Lists files and directories in a given path. Returns formatted directory listing.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> pathProp = new HashMap<>();
        pathProp.put("type", "string");
        pathProp.put("description", "The directory path to list (default: current directory)");
        properties.put("path", pathProp);

        schema.put("properties", properties);
        schema.put("required", new String[]{});

        return schema;
    }

    @Override
    public String execute(Map<String, Object> arguments) throws Exception {
        String pathStr = (String) arguments.getOrDefault("path", ".");
        Path path = Paths.get(pathStr);

        if (!Files.exists(path)) {
            return "Error: Path not found: " + pathStr;
        }

        if (!Files.isDirectory(path)) {
            return "Error: Path is not a directory: " + pathStr;
        }

        try (Stream<Path> paths = Files.list(path)) {
            String listing = paths
                    .sorted()
                    .map(p -> {
                        String name = p.getFileName().toString();
                        if (Files.isDirectory(p)) {
                            return "[DIR]  " + name;
                        } else {
                            try {
                                long size = Files.size(p);
                                return String.format("[FILE] %s (%d bytes)", name, size);
                            } catch (IOException e) {
                                return "[FILE] " + name;
                            }
                        }
                    })
                    .collect(Collectors.joining("\n"));

            log.info("Listed directory: {}", pathStr);
            return "Directory listing for: " + path.toAbsolutePath() + "\n\n" + listing;
        }
    }
}

package com.airepublic.t1.mcp;

import lombok.Data;
import java.util.Map;

@Data
public class MCPTool {
    private String name;
    private String description;
    private Map<String, Object> inputSchema;
}

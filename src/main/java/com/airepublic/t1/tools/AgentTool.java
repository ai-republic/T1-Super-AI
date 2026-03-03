package com.airepublic.t1.tools;

import java.util.Map;

public interface AgentTool {
    String getName();
    String getDescription();
    Map<String, Object> getParameterSchema();
    String execute(Map<String, Object> arguments) throws Exception;
}

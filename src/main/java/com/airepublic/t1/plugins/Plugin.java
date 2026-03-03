package com.airepublic.t1.plugins;

import com.airepublic.t1.tools.AgentTool;

import java.util.List;
import java.util.Map;

/**
 * Legacy Plugin interface for JAR-based Java plugins.
 *
 * @deprecated This interface is no longer used. Plugins are now defined via plugin.json files
 * containing tool definitions. See PluginManager.PluginInfo for the new plugin model.
 *
 * New plugin structure:
 * .t1-super-ai/plugins/
 *   └── plugin-name/
 *       ├── plugin.json       (tool definitions)
 *       ├── README.md         (documentation)
 *       └── src/              (executable scripts)
 */
@Deprecated(since = "1.0.0", forRemoval = true)
public interface Plugin {
    String getName();
    String getVersion();
    String getDescription();
    void initialize(Map<String, Object> config) throws Exception;
    void shutdown();
    List<AgentTool> getTools();
}

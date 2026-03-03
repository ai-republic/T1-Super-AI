package com.airepublic.t1.config;

import com.airepublic.t1.agent.ToolRegistry;
import com.airepublic.t1.tools.AgentTool;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;

/**
 * Configuration class that registers AgentTools as Spring function beans.
 * This allows Spring AI ChatClient to discover and use them via the .functions() method.
 *
 * Each AgentTool is wrapped as a Function<Map, String> bean that Spring AI can invoke.
 * The function name matches the tool name, so Spring AI can find it by name.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ToolFunctionConfiguration {

    private final ToolRegistry toolRegistry;
    private final ApplicationContext applicationContext;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Dynamically registers all AgentTools as function beans.
     * This method should be called AFTER the MCP server connection is established
     * to ensure all MCP tools are available.
     *
     * Note: This is called from AiSuperAgentApplication AFTER MCPClient connects.
     */
    public void registerToolFunctions() {
        Collection<AgentTool> tools = toolRegistry.getAllTools();
        log.info("Registering {} tools as Spring function beans", tools.size());

        if (tools.isEmpty()) {
            log.warn("⚠️  No tools available to register - MCP connection may not be established yet");
            return;
        }

        if (applicationContext instanceof ConfigurableApplicationContext) {
            ConfigurableListableBeanFactory beanFactory =
                    ((ConfigurableApplicationContext) applicationContext).getBeanFactory();

            int registered = 0;
            int skipped = 0;

            for (AgentTool tool : tools) {
                String beanName = tool.getName();

                // Check if bean already exists to avoid conflicts
                if (beanFactory.containsSingleton(beanName)) {
                    log.debug("Skipping bean {} - already registered", beanName);
                    skipped++;
                    continue;
                }

                // Create a Function<Map, String> that wraps the AgentTool
                Function<Map<String, Object>, String> function = args -> {
                    try {
                        log.debug("Executing tool function: {} with args: {}", tool.getName(), args);
                        return tool.execute(args);
                    } catch (Exception e) {
                        log.error("Error executing tool: {}", tool.getName(), e);
                        return "Error: " + e.getMessage();
                    }
                };

                // Register as singleton bean
                beanFactory.registerSingleton(beanName, function);
                log.debug("Registered function bean: {} -> {}", beanName, tool.getClass().getSimpleName());
                registered++;
            }

            if (skipped > 0) {
                log.info("Skipped {} already-registered tool functions", skipped);
            }

            log.info("✅ Successfully registered {} tool functions as Spring beans ({} skipped as duplicates)", registered, skipped);
        } else {
            log.error("❌ Cannot register tool functions - ApplicationContext is not configurable");
        }
    }
}

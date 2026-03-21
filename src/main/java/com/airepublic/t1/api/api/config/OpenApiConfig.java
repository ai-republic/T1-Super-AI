package com.airepublic.t1.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI t1SuperAiOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("T1 Super AI REST API")
                        .description("""
                                Comprehensive REST API for T1 Super AI - A powerful multi-agent system with LLM integration.

                                ## Features
                                - Multi-agent management with independent sessions
                                - Support for multiple LLM providers (OpenAI, Anthropic, Ollama)
                                - Plugin and skill system
                                - MCP (Model Context Protocol) integration
                                - Real-time chat and streaming capabilities
                                - Persistent agent configurations

                                ## Quick Start
                                1. List available agents: `GET /api/v1/agents`
                                2. Create a new agent: `POST /api/v1/agents`
                                3. Configure the agent: `PUT /api/v1/agents/{name}`
                                4. Send messages: `POST /api/v1/agents/{name}/message`

                                ## Agent Persistence
                                All agents are automatically saved to `~/.t1-super-ai/agents/` and loaded at startup.
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("T1 Super AI Team")
                                .email("support@t1superai.dev")
                                .url("https://github.com/airepublic/t1-super-ai"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0.html")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("Local Development Server"),
                        new Server()
                                .url("https://api.example.com")
                                .description("Production Server")
                ))
                .tags(List.of(
                        new Tag().name("Agent Management").description("Operations for managing AI agents (CRUD operations)"),
                        new Tag().name("Agent Messaging").description("Send messages and interact with specific agents"),
                        new Tag().name("Chat").description("General chat operations"),
                        new Tag().name("Configuration").description("System and LLM configuration management"),
                        new Tag().name("Plugins").description("Plugin management operations"),
                        new Tag().name("Skills").description("Skill management operations"),
                        new Tag().name("MCP").description("Model Context Protocol server management")
                ))
                .components(new Components());
    }
}

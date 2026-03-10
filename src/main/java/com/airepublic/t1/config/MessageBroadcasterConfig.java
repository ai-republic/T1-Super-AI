package com.airepublic.t1.config;

import com.airepublic.t1.api.websocket.ChatWebSocketHandler;
import com.airepublic.t1.service.MessageBroadcaster;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for wiring up MessageBroadcaster to output channels.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class MessageBroadcasterConfig {

    private final MessageBroadcaster messageBroadcaster;
    private final ChatWebSocketHandler chatWebSocketHandler;

    @Bean
    public CommandLineRunner wireMessageBroadcaster() {
        return args -> {
            // Wire WebSocket handler for web UI broadcasting
            messageBroadcaster.setWebSocketHandler(chatWebSocketHandler);
            log.debug("MessageBroadcaster wired to WebSocket handler");
        };
    }
}

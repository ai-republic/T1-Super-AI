package com.airepublic.t1.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Spring Boot condition that checks if the agent configuration has a default provider set.
 * This is used to conditionally create beans that depend on LLM configuration.
 */
@Slf4j
public class DefaultProviderConfiguredCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        try {
            // Get the AgentConfigurationManager bean
            AgentConfigurationManager configManager = context.getBeanFactory().getBean(AgentConfigurationManager.class);
            
            boolean isConfigured = configManager.isConfigured();
            
            if (!isConfigured) {
                log.warn("⚠️ No default LLM provider configured yet. Vector store and embedding features will be disabled.");
                log.info("💡 Please run the configuration wizard to set up your LLM providers");
            }
            
            return isConfigured;
        } catch (Exception e) {
            log.error("Failed to check default provider configuration", e);
            return false;
        }
    }
}

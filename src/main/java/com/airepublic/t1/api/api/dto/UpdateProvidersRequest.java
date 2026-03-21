package com.airepublic.t1.api.dto;

import com.airepublic.t1.model.AgentConfiguration;
import lombok.Data;

import java.util.Map;

@Data
public class UpdateProvidersRequest {
    private Map<AgentConfiguration.LLMProvider, ProviderConfigUpdate> providers;

    @Data
    public static class ProviderConfigUpdate {
        private String apiKey;
        private String baseUrl;
        private String model;
    }
}

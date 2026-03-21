package com.airepublic.t1.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class WebFetchTool implements AgentTool {
    private final WebClient webClient;

    public WebFetchTool() {
        this.webClient = WebClient.builder()
                .build();
    }

    @Override
    public String getName() {
        return "web_fetch";
    }

    @Override
    public String getDescription() {
        return "Fetches content from a URL and returns it. Can retrieve web pages, API responses, etc.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new HashMap<>();

        Map<String, Object> urlProp = new HashMap<>();
        urlProp.put("type", "string");
        urlProp.put("description", "The URL to fetch");
        properties.put("url", urlProp);

        schema.put("properties", properties);
        schema.put("required", new String[]{"url"});

        return schema;
    }

    @Override
    public String execute(Map<String, Object> arguments) throws Exception {
        String url = (String) arguments.get("url");

        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("url is required");
        }

        log.info("Fetching URL: {}", url);

        try {
            String response = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            log.info("Successfully fetched URL: {} ({} bytes)", url,
                    response != null ? response.length() : 0);

            return response != null ? response : "";
        } catch (Exception e) {
            log.error("Error fetching URL: {}", url, e);
            return "Error fetching URL: " + e.getMessage();
        }
    }
}

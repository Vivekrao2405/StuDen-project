package com.studen.aicoach;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * Thin wrapper around OpenAI's Chat Completions API — construction mirrors
 * {@code com.studen.practical.judge.RemoteCodeExecutionService} exactly (injected
 * {@code RestClient.Builder}, {@code JdkClientHttpRequestFactory}, plain
 * {@code Map<String,Object>} request / {@code JsonNode} response, no SDK dependency). One
 * deliberate deviation: {@link #isAvailable()} is a pure "configured" flag, not a live health
 * check — OpenAI is a billed third-party API, not self-hosted infra to poll on every button
 * render.
 */
@Component
public class OpenAiCoachClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCoachClient.class);

    private final RestClient restClient;
    private final AiCoachProperties properties;
    private final boolean configured;

    @Autowired
    public OpenAiCoachClient(RestClient.Builder restClientBuilder, AiCoachProperties properties) {
        this(buildRestClient(restClientBuilder, properties), properties,
                properties.isEnabled() && !properties.getApiKey().isBlank());
    }

    // Test-only seam: takes an already-fully-configured RestClient (e.g. bound to
    // MockRestServiceServer) instead of building one from scratch. Production/Spring only ever
    // calls the two-arg @Autowired constructor above.
    OpenAiCoachClient(RestClient restClient, AiCoachProperties properties, boolean configured) {
        this.restClient = restClient;
        this.properties = properties;
        this.configured = configured;
        if (!configured) {
            log.warn("OpenAI is not configured (OPENAI_API_KEY missing or app.ai-coach.enabled=false) — "
                    + "AI Coach will report unavailable");
        }
    }

    private static RestClient buildRestClient(RestClient.Builder restClientBuilder, AiCoachProperties properties) {
        boolean configured = properties.isEnabled() && !properties.getApiKey().isBlank();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory();
        requestFactory.setReadTimeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()));
        return restClientBuilder
                .baseUrl(configured ? properties.getBaseUrl() : "http://unconfigured.invalid")
                .requestFactory(requestFactory)
                .defaultHeader("Authorization", "Bearer " + properties.getApiKey())
                .build();
    }

    public boolean isAvailable() {
        return configured;
    }

    /** Returns the assistant's reply text, or throws {@link AiCoachUnavailableException}. */
    public String complete(String systemPrompt, String userPrompt) {
        if (!configured) {
            throw new AiCoachUnavailableException("AI Coach is not configured yet");
        }
        try {
            Map<String, Object> systemMessage = new LinkedHashMap<>();
            systemMessage.put("role", "system");
            systemMessage.put("content", systemPrompt);
            Map<String, Object> userMessage = new LinkedHashMap<>();
            userMessage.put("role", "user");
            userMessage.put("content", userPrompt);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", properties.getModel());
            body.put("messages", List.of(systemMessage, userMessage));
            body.put("max_tokens", properties.getMaxOutputTokens());
            body.put("temperature", 0.3);

            JsonNode result = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            String content = extractContent(result);
            if (content == null || content.isBlank()) {
                throw new AiCoachUnavailableException("AI Coach returned an empty response");
            }
            return content;
        } catch (AiCoachUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.error("OpenAI chat completion call failed", e);
            throw new AiCoachUnavailableException("AI Coach is temporarily unavailable — please try again shortly");
        }
    }

    private static String extractContent(JsonNode result) {
        if (result == null) {
            return null;
        }
        JsonNode choices = result.get("choices");
        if (choices == null || !choices.isArray() || choices.isEmpty()) {
            return null;
        }
        JsonNode message = choices.get(0).get("message");
        return textOrNull(message, "content");
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}

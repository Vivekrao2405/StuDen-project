package com.studen.aicoach;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Mirrors {@code com.studen.practical.execution.ExecutionProperties}'s shape exactly — every
 * external-service config knob with a Java default, secret defaulting to blank so a missing
 * {@code OPENAI_API_KEY} degrades the AI Coach to "unavailable" rather than failing app startup.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.ai-coach")
public class AiCoachProperties {

    private boolean enabled = true;
    private String apiKey = "";
    private String model = "gpt-4o-mini";
    private String baseUrl = "https://api.openai.com/v1";
    private int requestTimeoutSeconds = 30;
    private int maxOutputTokens = 600;
    private int maxRequestsPerWindow = 30;
    private int windowMinutes = 60;
    // How many prior interactions on the same question to feed back as conversational context.
    private int historyContextSize = 4;
}

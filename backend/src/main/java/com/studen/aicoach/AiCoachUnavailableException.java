package com.studen.aicoach;

// Thrown by AiCoachService when OpenAiCoachClient is unconfigured (no OPENAI_API_KEY) or the
// OpenAI call itself fails -- mapped to HTTP 503 by GlobalExceptionHandler, same shape as
// com.studen.practical.execution.ExecutionServiceUnavailableException. Never a fake 200.
public class AiCoachUnavailableException extends RuntimeException {

    public AiCoachUnavailableException(String message) {
        super(message);
    }
}

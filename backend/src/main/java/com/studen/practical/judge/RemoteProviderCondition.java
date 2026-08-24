package com.studen.practical.judge;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * True when the CODING execution sandbox is on ({@code app.execution.enabled}, default true) and
 * the remote provider is selected ({@code app.execution.provider=remote}) — the production
 * (Render) case, since no Docker Engine is reachable there. See {@link RemoteCodeExecutionService},
 * which talks to the separate self-hosted {@code execution-server} app, and
 * {@link DockerProviderCondition} for the local-dev counterpart.
 */
public class RemoteProviderCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        boolean enabled = context.getEnvironment().getProperty("app.execution.enabled", Boolean.class, true);
        String provider = context.getEnvironment().getProperty("app.execution.provider", "docker");
        return enabled && "remote".equalsIgnoreCase(provider);
    }
}

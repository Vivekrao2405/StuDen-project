package com.studen.practical.judge;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * True when the CODING execution sandbox is on ({@code app.execution.enabled}, default true) and
 * the docker provider is selected ({@code app.execution.provider}, default "docker"). A shared
 * class (rather than a repeated {@code @ConditionalOnExpression} SpEL string) so
 * {@link DockerCodeExecutionService} and its counterpart {@link Judge0ProviderCondition} can never
 * drift out of sync with each other.
 */
public class DockerProviderCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        boolean enabled = context.getEnvironment().getProperty("app.execution.enabled", Boolean.class, true);
        String provider = context.getEnvironment().getProperty("app.execution.provider", "docker");
        return enabled && "docker".equalsIgnoreCase(provider);
    }
}

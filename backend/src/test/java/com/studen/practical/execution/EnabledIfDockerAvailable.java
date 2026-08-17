package com.studen.practical.execution;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Marks a test class/method as requiring a real, reachable Docker Engine -- the {@code
 * studen-code-runner} image must also be built locally ({@code docker build -t
 * studen-code-runner:latest backend/docker/runner}). Skipped (not failed) when Docker isn't
 * available, via {@link DockerAvailableCondition}.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(DockerAvailableCondition.class)
public @interface EnabledIfDockerAvailable {
}

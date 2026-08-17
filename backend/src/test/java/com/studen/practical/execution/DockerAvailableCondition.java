package com.studen.practical.execution;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import java.time.Duration;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Skips {@link EnabledIfDockerAvailable}-annotated tests when no Docker Engine is reachable, so
 * the rest of this repo's suite (see {@link com.studen.practical.PracticalAttemptControllerTest})
 * stays green on any machine/CI without Docker installed. Probed once per JVM run and cached --
 * these tests spin up real containers, so a slow/absent Docker daemon shouldn't be re-probed for
 * every test method.
 */
public class DockerAvailableCondition implements ExecutionCondition {

    private static volatile Boolean available;

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        return isAvailable()
                ? ConditionEvaluationResult.enabled("Docker Engine reachable")
                : ConditionEvaluationResult.disabled("Docker Engine not reachable -- skipping sandbox integration test");
    }

    private static boolean isAvailable() {
        if (available == null) {
            synchronized (DockerAvailableCondition.class) {
                if (available == null) {
                    available = probe();
                }
            }
        }
        return available;
    }

    private static boolean probe() {
        try {
            DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
            DockerHttpClient httpClient = new ZerodepDockerHttpClient.Builder()
                    .dockerHost(config.getDockerHost())
                    .sslConfig(config.getSSLConfig())
                    .connectionTimeout(Duration.ofSeconds(2))
                    .responseTimeout(Duration.ofSeconds(5))
                    .build();
            DockerClient client = DockerClientImpl.getInstance(config, httpClient);
            client.pingCmd().exec();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

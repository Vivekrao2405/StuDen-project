package com.studen.practical.execution;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the single shared {@link DockerClient} used by both
 * {@code com.studen.practical.judge.DockerCodeExecutionService} and
 * {@code com.studen.practical.judge.DockerSqlExecutionService}. {@code app.execution.docker-host}
 * empty (the default) resolves via docker-java's own default (the {@code DOCKER_HOST} env var, or
 * the platform default local named pipe/socket) -- local Docker Desktop needs zero configuration.
 * Set it to {@code tcp://host:2376} with TLS for a remote production Docker Engine; the main
 * backend process itself never runs Docker.
 */
@Configuration
public class DockerClientFactory {

    @Bean
    @ConditionalOnProperty(prefix = "app.execution", name = "enabled", havingValue = "true", matchIfMissing = true)
    public DockerClient dockerClient(ExecutionProperties properties) {
        DefaultDockerClientConfig.Builder configBuilder = DefaultDockerClientConfig.createDefaultConfigBuilder();
        if (!properties.getDockerHost().isBlank()) {
            configBuilder.withDockerHost(properties.getDockerHost());
        }
        configBuilder.withDockerTlsVerify(properties.isDockerTlsVerify());
        if (!properties.getDockerCertPath().isBlank()) {
            configBuilder.withDockerCertPath(properties.getDockerCertPath());
        }
        DefaultDockerClientConfig config = configBuilder.build();

        DockerHttpClient httpClient = new ZerodepDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(Math.max(properties.getMaxConcurrency() * 2, 10))
                // Short on purpose: isAvailable() calls this on every Run/Submit, and a
                // fully-unreachable Docker Engine (down, misconfigured DOCKER_HOST) should fail
                // fast into the honest SYSTEM_ERROR path rather than stall the request.
                .connectionTimeout(Duration.ofSeconds(3))
                .responseTimeout(Duration.ofSeconds(60))
                .build();

        return DockerClientImpl.getInstance(config, httpClient);
    }
}

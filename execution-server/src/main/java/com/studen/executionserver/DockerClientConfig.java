package com.studen.executionserver;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Mirrors the main backend's {@code DockerClientFactory} almost verbatim. {@code app.docker-host}
 * empty (the default) resolves via docker-java's own default (the {@code DOCKER_HOST} env var, or
 * the platform default local socket/named pipe) -- when this app runs Docker-outside-of-Docker
 * with the host socket bind-mounted in, that default local-socket resolution is exactly right.
 */
@Configuration
public class DockerClientConfig {

    @Bean
    public DockerClient dockerClient(ExecutionServerProperties properties) {
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
                .maxConnections(20)
                .connectionTimeout(Duration.ofSeconds(3))
                .responseTimeout(Duration.ofSeconds(60))
                .build();

        return DockerClientImpl.getInstance(config, httpClient);
    }
}

package com.studen.practical.execution;

import java.util.concurrent.Semaphore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// A fair Semaphore is enough to bound concurrent Docker containers: Run/Submit are synchronous,
// blocking calls (the container-execution I/O already blocks the calling servlet thread), so no
// separate thread pool is needed on top -- same "small, dedicated, scoped resource" spirit as
// com.studen.notification.NotificationAsyncConfig, minus the executor this flow doesn't need.
@Configuration
public class ExecutionAsyncConfig {

    @Bean
    public Semaphore executionSemaphore(ExecutionProperties properties) {
        return new Semaphore(properties.getMaxConcurrency(), true);
    }
}

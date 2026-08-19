package com.studen.communication;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

// A dedicated executor for campaign delivery — deliberately NOT the notification package's
// notificationTaskExecutor, which that package's own config explicitly scopes to push dispatch
// only. Small pool: campaign sends are already internally batched/rate-limited in
// CampaignDeliveryWorker, so this only needs enough concurrency to run one campaign's batches
// without blocking the @Scheduled poller thread.
@Configuration
@EnableAsync
public class CommunicationAsyncConfig {

    // Gated (same app.execution.enabled idiom as com.studen.practical's Docker sandbox) so
    // CommunicationTestSupport can supply a synchronous test executor under the same bean name
    // with zero registration-order ambiguity — tests set app.communication.async.enabled=false so
    // this bean is never even registered, rather than trying to override it.
    @Bean("communicationTaskExecutor")
    @ConditionalOnProperty(prefix = "app.communication.async", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    public ThreadPoolTaskExecutor communicationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("campaign-send-");
        executor.initialize();
        return executor;
    }
}

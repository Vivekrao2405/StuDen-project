package com.studen.notification;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

// Scoped to this package only — a small dedicated executor for push delivery, not a system-wide
// default executor change. No other async work in the app should route through this bean.
@Configuration
@EnableAsync
public class NotificationAsyncConfig {

    @Bean("notificationTaskExecutor")
    public ThreadPoolTaskExecutor notificationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("push-notif-");
        executor.initialize();
        return executor;
    }
}

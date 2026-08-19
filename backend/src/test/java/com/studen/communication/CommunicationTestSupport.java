package com.studen.communication;

import com.studen.communication.email.EmailService;
import java.util.concurrent.Executor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.SyncTaskExecutor;

/**
 * Test-only bean overrides, imported by communication test classes together with
 * {@code @TestPropertySource(properties = "app.communication.async.enabled=false")}:
 * <ul>
 * <li>{@link FakeEmailService} replaces {@code ResendEmailService} (different bean name, so
 * ordinary {@code @Primary}-based autowire resolution is enough) so no test ever calls the real
 * Resend API.</li>
 * <li>{@code communicationTaskExecutor} runs synchronously. With {@code
 * app.communication.async.enabled=false}, {@code CommunicationAsyncConfig}'s own executor bean of
 * the same name never gets registered at all (its {@code @ConditionalOnProperty} is false), so
 * this bean is the only candidate under that name — no bean-definition-override or registration-
 * order ambiguity. This also makes {@code CampaignDeliveryWorker.processCampaignAsync} run
 * in-line on the calling (test) thread, sharing the same still-open test transaction as the
 * MockMvc call that triggered it — otherwise a cross-thread {@code @Async} worker could never see
 * this test's own uncommitted writes.</li>
 * </ul>
 */
@TestConfiguration
public class CommunicationTestSupport {

    @Bean
    @Primary
    public EmailService emailService() {
        return new FakeEmailService();
    }

    @Bean("communicationTaskExecutor")
    public Executor communicationTaskExecutor() {
        return new SyncTaskExecutor();
    }
}

package com.studen.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.security.Security;
import java.util.List;
import java.util.UUID;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

/**
 * The first plain Mockito unit test in this repo (every other test class is a full
 * {@code @SpringBootTest}) — deliberate here because simulating a real push-service 404/410/5xx
 * response through a full business-event MockMvc flow would be heavy and indirect. A real
 * {@link ObjectMapper} is used (payload serialization isn't what's under test); {@link PushService}
 * and {@link PushSubscriptionRepository} are mocked so the HTTP response status can be controlled
 * directly. {@code dispatchAsync} is called directly here (no Spring proxy involved when
 * constructing the class by hand), which is fine — {@code @Async} only matters for beans obtained
 * through the application context; calling the method directly just runs it synchronously.
 */
@ExtendWith(MockitoExtension.class)
class PushDispatcherTest {

    // web-push's Notification constructor eagerly parses the subscription's p256dh key via a
    // KeyFactory bound to the "BC" provider — in production PushServiceConfig's constructor
    // registers it once at Spring startup, but this test has no Spring context, so it must be
    // registered by hand here, mirroring that same bootstrap step.
    @BeforeAll
    static void registerBouncyCastle() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Mock
    private PushSubscriptionRepository pushSubscriptionRepository;

    @Mock
    private PushService pushService;

    private PushDispatcher dispatcher() {
        return new PushDispatcher(pushSubscriptionRepository, pushService, new ObjectMapper());
    }

    // web-push eagerly parses p256dh as a real EC point (see registerBouncyCastle's comment
    // above), so these must be syntactically valid base64url — a real 65-byte uncompressed
    // P-256 public key and a real 16-byte auth secret, not arbitrary placeholder text.
    private static final String VALID_P256DH = "BKmtcng8tooKRxx6faZzm_UwmGM3Dv8dEA6taFeJQ5qRi4Yu79cs6SJWaCt_kRkpDuleJ1ypcohkeY0bQY6rYHU";
    private static final String VALID_AUTH = "tBHItJI5svbpez7KI4CCXg";

    private PushSubscription subscription(String endpoint) {
        PushSubscription subscription = new PushSubscription();
        subscription.setEndpoint(endpoint);
        subscription.setP256dh(VALID_P256DH);
        subscription.setAuth(VALID_AUTH);
        subscription.setActive(true);
        return subscription;
    }

    private HttpResponse responseWithStatus(int statusCode) {
        StatusLine statusLine = mock(StatusLine.class);
        when(statusLine.getStatusCode()).thenReturn(statusCode);
        HttpResponse response = mock(HttpResponse.class);
        when(response.getStatusLine()).thenReturn(statusLine);
        return response;
    }

    @Test
    void dispatchAsync_successfulSend_doesNotDeactivateSubscription() throws Exception {
        PushSubscription subscription = subscription("https://push.example.com/ok");
        UUID userId = UUID.randomUUID();
        when(pushSubscriptionRepository.findAllByUserIdAndActiveTrue(userId)).thenReturn(List.of(subscription));
        HttpResponse response = responseWithStatus(201);
        when(pushService.send(any())).thenReturn(response);

        dispatcher().dispatchAsync(userId, NotificationType.NEW_MESSAGE, "Hi", UUID.randomUUID(), "/messages/1");

        verify(pushSubscriptionRepository, never()).save(any());
    }

    @Test
    void dispatchAsync_send404_deactivatesSubscription() throws Exception {
        PushSubscription subscription = subscription("https://push.example.com/gone");
        UUID userId = UUID.randomUUID();
        when(pushSubscriptionRepository.findAllByUserIdAndActiveTrue(userId)).thenReturn(List.of(subscription));
        HttpResponse response = responseWithStatus(404);
        when(pushService.send(any())).thenReturn(response);

        dispatcher().dispatchAsync(userId, NotificationType.NEW_MESSAGE, "Hi", UUID.randomUUID(), "/messages/1");

        assertThat(subscription.isActive()).isFalse();
        verify(pushSubscriptionRepository).save(subscription);
    }

    @Test
    void dispatchAsync_send410_deactivatesSubscription() throws Exception {
        PushSubscription subscription = subscription("https://push.example.com/expired");
        UUID userId = UUID.randomUUID();
        when(pushSubscriptionRepository.findAllByUserIdAndActiveTrue(userId)).thenReturn(List.of(subscription));
        HttpResponse response = responseWithStatus(410);
        when(pushService.send(any())).thenReturn(response);

        dispatcher().dispatchAsync(userId, NotificationType.NEW_MESSAGE, "Hi", UUID.randomUUID(), "/messages/1");

        assertThat(subscription.isActive()).isFalse();
        verify(pushSubscriptionRepository).save(subscription);
    }

    @Test
    void dispatchAsync_sendThrows_doesNotPropagateAndSubscriptionStaysActive() throws Exception {
        PushSubscription subscription = subscription("https://push.example.com/flaky");
        UUID userId = UUID.randomUUID();
        when(pushSubscriptionRepository.findAllByUserIdAndActiveTrue(userId)).thenReturn(List.of(subscription));
        when(pushService.send(any())).thenThrow(new IOException("push service unreachable"));

        assertThatNoException().isThrownBy(() ->
                dispatcher().dispatchAsync(userId, NotificationType.NEW_MESSAGE, "Hi", UUID.randomUUID(), "/messages/1"));

        assertThat(subscription.isActive()).isTrue();
        verify(pushSubscriptionRepository, never()).save(any());
    }

    @Test
    void dispatchAsync_noActiveSubscriptions_neverCallsPushService() {
        UUID userId = UUID.randomUUID();
        when(pushSubscriptionRepository.findAllByUserIdAndActiveTrue(userId)).thenReturn(List.of());

        assertThatNoException().isThrownBy(() ->
                dispatcher().dispatchAsync(userId, NotificationType.NEW_MESSAGE, "Hi", UUID.randomUUID(), "/messages/1"));

        verifyNoInteractions(pushService);
    }
}

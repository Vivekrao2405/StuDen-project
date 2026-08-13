package com.studen.notification;

import java.util.List;
import java.util.UUID;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Subscription;
import org.apache.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * The actual network call to a third-party push service, kept in a separate bean from
 * {@link NotificationService} so {@code @Async} can intercept it via Spring's proxy — calling an
 * {@code @Async} method on {@code this} from within the same class silently runs synchronously,
 * so the split is required, not stylistic. Every subscription is attempted independently: one
 * bad/expired subscription must never stop delivery to the rest, and no failure here is ever
 * allowed to propagate back to the caller's business transaction.
 */
@Service
public class PushDispatcher {

    private static final Logger log = LoggerFactory.getLogger(PushDispatcher.class);
    private static final String TITLE = "StuDen";

    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final PushService pushService;
    private final ObjectMapper objectMapper;

    public PushDispatcher(PushSubscriptionRepository pushSubscriptionRepository, PushService pushService,
            ObjectMapper objectMapper) {
        this.pushSubscriptionRepository = pushSubscriptionRepository;
        this.pushService = pushService;
        this.objectMapper = objectMapper;
    }

    @Async("notificationTaskExecutor")
    public void dispatchAsync(UUID userId, NotificationType type, String message, UUID resourceId, String url) {
        List<PushSubscription> subscriptions = pushSubscriptionRepository.findAllByUserIdAndActiveTrue(userId);
        if (subscriptions.isEmpty()) {
            return;
        }

        String payload;
        try {
            String tag = type.name() + ":" + resourceId;
            payload = objectMapper.writeValueAsString(new PushPayload(TITLE, message, type.name(), resourceId, url, tag));
        } catch (RuntimeException ex) {
            log.warn("Failed to build push payload for user {} type {}", userId, type, ex);
            return;
        }

        for (PushSubscription subscription : subscriptions) {
            sendToSubscription(subscription, payload);
        }
    }

    private void sendToSubscription(PushSubscription subscription, String payload) {
        try {
            Subscription target = new Subscription(subscription.getEndpoint(),
                    new Subscription.Keys(subscription.getP256dh(), subscription.getAuth()));
            nl.martijndwars.webpush.Notification pushNotification =
                    new nl.martijndwars.webpush.Notification(target, payload);

            HttpResponse response = pushService.send(pushNotification);
            int status = response.getStatusLine().getStatusCode();

            if (status == 404 || status == 410) {
                // The push service itself says this subscription is permanently gone (browser
                // unregistered it, uninstalled, storage cleared, etc) — deactivate rather than
                // keep retrying a channel that will never accept another push.
                subscription.setActive(false);
                pushSubscriptionRepository.save(subscription);
            } else if (status >= 300) {
                log.warn("Push delivery to subscription {} returned status {}", subscription.getId(), status);
            }
        } catch (Exception ex) {
            // Network failure, transient push-service outage, or a crypto/JWT error — logged and
            // swallowed here, never rethrown. This method runs on the async executor with no
            // caller waiting on it, so an uncaught exception here would only ever be silently
            // dropped by Spring's default AsyncUncaughtExceptionHandler; logging explicitly avoids
            // depending on that default.
            log.warn("Push delivery to subscription {} failed", subscription.getId(), ex);
        }
    }

    private record PushPayload(String title, String body, String type, UUID resourceId, String url, String tag) {
    }
}

package com.studen.notification;

import com.studen.user.UserRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PushSubscriptionService {

    private static final int MAX_USER_AGENT_LENGTH = 255;

    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final UserRepository userRepository;

    public PushSubscriptionService(PushSubscriptionRepository pushSubscriptionRepository, UserRepository userRepository) {
        this.pushSubscriptionRepository = pushSubscriptionRepository;
        this.userRepository = userRepository;
    }

    // Upsert-by-endpoint: a browser subscription endpoint is inherently unique per (browser
    // install, origin). Re-subscribing the same browser (permission reset, storage cleared) or a
    // different user logging into the same shared/kiosk device must reassign this row rather than
    // create a duplicate that both point at the same push channel — this is also what makes
    // "user logs out, different user logs in on the same device" correctly re-associate the
    // endpoint instead of silently leaving it (and its future pushes) attached to the old user.
    @Transactional
    public PushSubscriptionResponse register(UUID userId, RegisterPushSubscriptionRequest request, String userAgent) {
        PushSubscription subscription = pushSubscriptionRepository.findByEndpoint(request.endpoint())
                .orElseGet(PushSubscription::new);

        subscription.setUser(userRepository.getReferenceById(userId));
        subscription.setEndpoint(request.endpoint());
        subscription.setP256dh(request.p256dh());
        subscription.setAuth(request.auth());
        subscription.setUserAgent(truncate(userAgent));
        subscription.setActive(true);

        subscription = pushSubscriptionRepository.save(subscription);
        return new PushSubscriptionResponse(subscription.getId());
    }

    // Ownership-scoped by endpoint AND userId, so this can never be used to remove another user's
    // subscription. Idempotent (no error if already gone) — the browser's own unsubscribe flow
    // just wants to be sure nothing's left registered, not to be told it succeeded twice.
    @Transactional
    public void unregister(UUID userId, String endpoint) {
        pushSubscriptionRepository.findByEndpointAndUserId(endpoint, userId)
                .ifPresent(pushSubscriptionRepository::delete);
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > MAX_USER_AGENT_LENGTH ? value.substring(0, MAX_USER_AGENT_LENGTH) : value;
    }
}

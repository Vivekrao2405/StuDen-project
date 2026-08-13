package com.studen.notification;

import java.util.UUID;

/**
 * Clean integration point for a real notification system, deliberately not built out yet — this
 * package was still just a {@code package-info.java} stub when Phase 6.5 needed to notify a
 * service request's provider/requester. {@link LoggingNotifier} is the only implementation for
 * now; a future phase can swap in a persisted/UI-backed implementation without touching any
 * caller.
 */
public interface Notifier {

    void notify(UUID userId, String message);
}

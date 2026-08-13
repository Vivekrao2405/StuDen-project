package com.studen.notification;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Replaces {@link LoggingNotifier} in every {@code @SpringBootTest} — picked up automatically by
 * component scanning since it shares the app's base package, same pattern as
 * {@code com.studen.storage.FakeMediaStorageService}. {@code LoggingNotifier} only logs, which
 * gives tests nothing to assert against; this records every call so a test can verify a
 * notification actually fired, and to whom.
 */
@Component
@Primary
public class RecordingNotifier implements Notifier {

    public record Recorded(UUID userId, String message) {
    }

    private final List<Recorded> notifications = new CopyOnWriteArrayList<>();

    @Override
    public void notify(UUID userId, String message) {
        notifications.add(new Recorded(userId, message));
    }

    public List<Recorded> notificationsFor(UUID userId) {
        return notifications.stream().filter(n -> n.userId().equals(userId)).toList();
    }

    public List<Recorded> all() {
        return List.copyOf(notifications);
    }

    public void clear() {
        notifications.clear();
    }
}

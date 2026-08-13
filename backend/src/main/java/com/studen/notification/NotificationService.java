package com.studen.notification;

import com.studen.common.exception.ResourceNotFoundException;
import com.studen.user.User;
import com.studen.user.UserRepository;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The sole production {@link Notifier}. Persisting the in-app row happens synchronously, inside
 * whatever transaction the caller (ServiceRequestService/OrderService/ConversationService) is
 * already in — a real failure there should roll back like any other write. Only the push send
 * itself (a call to a third-party service, the part that can be slow or flaky) is handed off to
 * {@link PushDispatcher}'s async executor, dispatched after this method returns.
 */
@Service
public class NotificationService implements Notifier {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final UserRepository userRepository;
    private final PushDispatcher pushDispatcher;

    public NotificationService(NotificationRepository notificationRepository,
            NotificationPreferenceRepository preferenceRepository, UserRepository userRepository,
            PushDispatcher pushDispatcher) {
        this.notificationRepository = notificationRepository;
        this.preferenceRepository = preferenceRepository;
        this.userRepository = userRepository;
        this.pushDispatcher = pushDispatcher;
    }

    @Override
    @Transactional
    public void notify(UUID userId, NotificationType type, String message, UUID resourceId) {
        Optional<NotificationPreference> preference = preferenceRepository.findByUserIdAndType(userId, type);
        // Absence means "not yet customized", not "opted out" — both channels default to enabled.
        boolean inAppEnabled = preference.map(NotificationPreference::isInAppEnabled).orElse(true);
        boolean pushEnabled = preference.map(NotificationPreference::isPushEnabled).orElse(true);

        String url = NotificationUrlBuilder.build(type, resourceId);

        if (inAppEnabled) {
            User user = userRepository.getReferenceById(userId);
            notificationRepository.save(new Notification(user, type, message, resourceId, url));
        }

        if (pushEnabled) {
            pushDispatcher.dispatchAsync(userId, type, message, resourceId, url);
        }
    }

    private static final int PAGE_SIZE = 30;

    @Transactional(readOnly = true)
    public List<NotificationResponse> list(UUID userId, Instant before) {
        Pageable pageable = PageRequest.of(0, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<Notification> notifications = before == null
                ? notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                : notificationRepository.findByUserIdAndCreatedAtBeforeOrderByCreatedAtDesc(userId, before, pageable);
        return notifications.stream().map(NotificationResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(UUID userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public void markRead(UUID userId, UUID notificationId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        notification.setRead(true);
        notification.setReadAt(Instant.now());
        notificationRepository.save(notification);
    }

    @Transactional
    public void markAllRead(UUID userId) {
        notificationRepository.markAllRead(userId, Instant.now());
    }

    @Transactional(readOnly = true)
    public List<NotificationPreferenceResponse> getPreferences(UUID userId) {
        var byType = preferenceRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(NotificationPreference::getType, p -> p));

        // Every one of the 7 types is always returned, even if the user has never customized it —
        // absence resolves to the enabled defaults, same rule notify() itself applies.
        return Arrays.stream(NotificationType.values())
                .map(type -> {
                    NotificationPreference existing = byType.get(type);
                    return existing == null
                            ? new NotificationPreferenceResponse(type, true, true)
                            : new NotificationPreferenceResponse(type, existing.isPushEnabled(), existing.isInAppEnabled());
                })
                .toList();
    }

    @Transactional
    public NotificationPreferenceResponse updatePreference(UUID userId, NotificationType type,
            UpdateNotificationPreferenceRequest request) {
        NotificationPreference preference = preferenceRepository.findByUserIdAndType(userId, type)
                .orElseGet(() -> new NotificationPreference(userRepository.getReferenceById(userId), type));
        preference.setPushEnabled(request.pushEnabled());
        preference.setInAppEnabled(request.inAppEnabled());
        preference = preferenceRepository.save(preference);
        return new NotificationPreferenceResponse(type, preference.isPushEnabled(), preference.isInAppEnabled());
    }
}

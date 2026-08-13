package com.studen.notification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

    List<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    List<Notification> findByUserIdAndCreatedAtBeforeOrderByCreatedAtDesc(
            UUID userId, Instant before, Pageable pageable);

    long countByUserIdAndReadFalse(UUID userId);

    @Modifying(clearAutomatically = true)
    @Query("update Notification n set n.read = true, n.readAt = :now where n.user.id = :userId and n.read = false")
    void markAllRead(UUID userId, Instant now);
}

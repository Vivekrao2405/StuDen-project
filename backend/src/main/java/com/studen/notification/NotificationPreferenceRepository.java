package com.studen.notification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, UUID> {

    Optional<NotificationPreference> findByUserIdAndType(UUID userId, NotificationType type);

    List<NotificationPreference> findByUserId(UUID userId);

    // Used by AdminUserService on permanent account deletion — a single-owner table, safe to
    // purge outright rather than anonymize (spec §20).
    @Modifying(clearAutomatically = true)
    @Query("delete from NotificationPreference p where p.user.id = :userId")
    void deleteAllByUserId(UUID userId);
}

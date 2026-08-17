package com.studen.notification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, UUID> {

    Optional<PushSubscription> findByEndpoint(String endpoint);

    Optional<PushSubscription> findByEndpointAndUserId(String endpoint, UUID userId);

    List<PushSubscription> findAllByUserIdAndActiveTrue(UUID userId);

    // Used by AdminUserService on permanent account deletion — a single-owner table, safe to
    // purge outright rather than anonymize (spec §20).
    @Modifying(clearAutomatically = true)
    @Query("delete from PushSubscription s where s.user.id = :userId")
    void deleteAllByUserId(UUID userId);
}

package com.studen.notification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, UUID> {

    Optional<PushSubscription> findByEndpoint(String endpoint);

    Optional<PushSubscription> findByEndpointAndUserId(String endpoint, UUID userId);

    List<PushSubscription> findAllByUserIdAndActiveTrue(UUID userId);
}

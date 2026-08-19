package com.studen.integrity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssessmentIntegrityEventRepository extends JpaRepository<AssessmentIntegrityEvent, UUID> {

    // Full event log for one attempt, oldest first -- the input to IntegrityScoringService's
    // pairing/scoring pass and to AdminIntegrityService's merged timeline.
    List<AssessmentIntegrityEvent> findAllByPracticalAttemptIdOrderByOccurredAtAsc(UUID practicalAttemptId);

    Optional<AssessmentIntegrityEvent> findByPracticalAttemptIdAndClientEventId(UUID practicalAttemptId, UUID clientEventId);

    boolean existsByPracticalAttemptIdAndClientEventId(UUID practicalAttemptId, UUID clientEventId);

    // Cooldown check for server-synthesized MULTIPLE_SESSION events -- don't spam one per
    // heartbeat while two tabs stay open.
    boolean existsByPracticalAttemptIdAndEventTypeAndOccurredAtAfter(UUID practicalAttemptId, IntegrityEventType eventType,
            Instant after);
}

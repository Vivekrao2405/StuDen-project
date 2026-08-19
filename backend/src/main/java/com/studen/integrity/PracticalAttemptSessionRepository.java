package com.studen.integrity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PracticalAttemptSessionRepository extends JpaRepository<PracticalAttemptSession, UUID> {

    Optional<PracticalAttemptSession> findByPracticalAttemptIdAndSessionId(UUID practicalAttemptId, String sessionId);

    // Distinct sessions still "alive" within the presence window -- more than one means multiple
    // concurrent tabs/sessions for the same attempt.
    List<PracticalAttemptSession> findAllByPracticalAttemptIdAndLastSeenAtAfter(UUID practicalAttemptId, Instant after);
}

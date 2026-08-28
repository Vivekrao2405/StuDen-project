package com.studen.calendar;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LearningSessionRepository extends JpaRepository<LearningSession, UUID> {

    // Calendar range query — always scoped to the caller's own student id (see CalendarService),
    // never a bare findAll/findById.
    List<LearningSession> findAllByStudentIdAndScheduledStartBetweenOrderByScheduledStartAsc(UUID studentId,
            Instant from, Instant to);

    // IDOR-safe single lookup — mirrors StudentResourceProgressRepository/PracticalAttemptRepository's
    // ownership-scoped-by-construction pattern; a session belonging to another student simply isn't
    // found, so the caller 404s instead of leaking existence.
    Optional<LearningSession> findByIdAndStudentId(UUID id, UUID studentId);

    // Conflict check for study-plan save (skip a slot that exactly matches an existing SCHEDULED
    // session rather than silently overwriting it).
    boolean existsByStudentIdAndResourceIdAndScheduledStartAndStatus(UUID studentId, UUID resourceId,
            Instant scheduledStart, LearningSessionStatus status);

    // Completion-sync: a resource marked COMPLETED directly (not via the calendar) auto-completes
    // any still-SCHEDULED session for that same (student, resource) pair.
    List<LearningSession> findAllByStudentIdAndResourceIdAndStatus(UUID studentId, UUID resourceId,
            LearningSessionStatus status);
}

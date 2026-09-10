package com.studen.aicoach;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiCoachInteractionRepository extends JpaRepository<AiCoachInteraction, UUID> {

    // History panel + prior-interaction context + HINT-level derivation, all for one student's one
    // question, chronological.
    List<AiCoachInteraction> findAllByPracticalAttemptQuestionIdAndUserIdOrderByCreatedAtAsc(
            UUID practicalAttemptQuestionId, UUID userId);

    // Rate-limit window: a user's requests across every question, mirrors
    // ServiceRequestRepository.countByRequesterIdAndCreatedAtAfter exactly.
    long countByUserIdAndCreatedAtAfter(UUID userId, Instant after);
}

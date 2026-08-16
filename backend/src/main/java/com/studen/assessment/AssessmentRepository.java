package com.studen.assessment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssessmentRepository extends JpaRepository<Assessment, UUID> {

    // IDOR guard for every learner-facing lookup — never a bare findById.
    @Query("select a from Assessment a join fetch a.skill where a.id = :id and a.user.id = :userId")
    Optional<Assessment> findByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    // Hot path on every "Start Assessment" click / resume-on-reopen — at most one row can match
    // since startOrResume only ever leaves a user with one IN_PROGRESS assessment per skill.
    Optional<Assessment> findByUserIdAndSkillIdAndStatus(UUID userId, UUID skillId, AssessmentStatus status);

    // Phase 7.3 "latest result" (spec §15) — "latest" is explicitly submittedAt-desc, never
    // scorePercentage-desc, so this must never be confused with a future "best result" query.
    // Bounded with Pageable(0, 1) for a real LIMIT 1 at the DB level rather than fetching every
    // completed attempt and taking the first in Java.
    @Query("""
            select a from Assessment a join fetch a.skill
            where a.user.id = :userId and a.status in :statuses
            order by a.submittedAt desc
            """)
    List<Assessment> findLatestByUserAndStatusIn(@Param("userId") UUID userId,
            @Param("statuses") List<AssessmentStatus> statuses, Pageable pageable);

    @Query("""
            select a from Assessment a join fetch a.skill
            where a.user.id = :userId and a.skill.id = :skillId and a.status in :statuses
            order by a.submittedAt desc
            """)
    List<Assessment> findLatestByUserAndSkillAndStatusIn(@Param("userId") UUID userId, @Param("skillId") UUID skillId,
            @Param("statuses") List<AssessmentStatus> statuses, Pageable pageable);

    // Atomic compare-and-set shared by submit() and the lazy expiry check, mirroring
    // WorkOrderRepository.submitIfInProgress/completeIfSubmitted — the WHERE re-checks
    // IN_PROGRESS at the database level so a double-click/expiry race can only ever succeed once.
    @Modifying(clearAutomatically = true)
    @Query("""
            update Assessment a
            set a.status = :status, a.submittedAt = :submittedAt, a.correctCount = :correctCount,
                a.scorePercentage = :scorePercentage
            where a.id = :id and a.status = com.studen.assessment.AssessmentStatus.IN_PROGRESS
            """)
    int finalizeIfInProgress(@Param("id") UUID id, @Param("status") AssessmentStatus status,
            @Param("submittedAt") Instant submittedAt, @Param("correctCount") int correctCount,
            @Param("scorePercentage") int scorePercentage);
}

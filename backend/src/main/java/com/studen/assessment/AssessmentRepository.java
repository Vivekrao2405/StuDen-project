package com.studen.assessment;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
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

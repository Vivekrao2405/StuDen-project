package com.studen.placement;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlacementAttemptRepository extends JpaRepository<PlacementAttempt, UUID> {

    List<PlacementAttempt> findByStudentIdOrderByStartedAtDesc(UUID studentId);

    // Guards assessment deletion: an assessment students have already attempted is archived,
    // never deleted, so attempt history survives.
    boolean existsByPlacementAssessmentId(UUID placementAssessmentId);

    // IDOR guard for every learner-facing lookup — never a bare findById (mirrors
    // AssessmentRepository.findByIdAndUserId).
    @Query("""
            select a from PlacementAttempt a
            join fetch a.placementAssessment pa join fetch pa.role
            where a.id = :id and a.student.id = :studentId
            """)
    Optional<PlacementAttempt> findByIdAndStudentId(@Param("id") UUID id, @Param("studentId") UUID studentId);

    // Hot path for start/resume — at most one row can match since startOrResume only ever leaves a
    // student with one IN_PROGRESS attempt per assessment.
    Optional<PlacementAttempt> findByStudentIdAndPlacementAssessmentIdAndStatus(UUID studentId,
            UUID placementAssessmentId, PlacementAttemptStatus status);

    // "Latest" = most recently submitted, across any assessment/role this student has attempted.
    // Bounded with Pageable(0, 1) for a real LIMIT 1 rather than fetch-all-then-take-first.
    @Query("""
            select a from PlacementAttempt a join fetch a.placementAssessment pa join fetch pa.role
            where a.student.id = :studentId and a.status in :statuses
            order by a.submittedAt desc
            """)
    List<PlacementAttempt> findLatestByStudentAndStatusIn(@Param("studentId") UUID studentId,
            @Param("statuses") List<PlacementAttemptStatus> statuses, Pageable pageable);

    // Atomic compare-and-set shared by submit() and the lazy expiry check, mirroring
    // AssessmentRepository.finalizeIfInProgress — the WHERE re-checks IN_PROGRESS at the database
    // level so a double-click/expiry race can only ever succeed once.
    @Modifying(clearAutomatically = true)
    @Query("""
            update PlacementAttempt a
            set a.status = :status, a.submittedAt = :submittedAt, a.score = :score, a.maxScore = :maxScore,
                a.scorePercentage = :scorePercentage
            where a.id = :id and a.status = com.studen.placement.PlacementAttemptStatus.IN_PROGRESS
            """)
    int finalizeIfInProgress(@Param("id") UUID id, @Param("status") PlacementAttemptStatus status,
            @Param("submittedAt") Instant submittedAt, @Param("score") int score, @Param("maxScore") int maxScore,
            @Param("scorePercentage") int scorePercentage);
}

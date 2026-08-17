package com.studen.practical;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PracticalAttemptRepository extends JpaRepository<PracticalAttempt, UUID> {

    // IDOR guard for every learner-facing lookup — never a bare findById, mirrors
    // AssessmentRepository.findByIdAndUserId exactly.
    @Query("select a from PracticalAttempt a join fetch a.practicalAssessment where a.id = :id and a.user.id = :userId")
    Optional<PracticalAttempt> findByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    // "Does this student already have an active attempt?" — the resume/duplicate-prevention check
    // on every "Start" click, same role as AssessmentRepository.findByUserIdAndSkillIdAndStatus.
    Optional<PracticalAttempt> findByUserIdAndPracticalAssessmentIdAndStatus(UUID userId, UUID practicalAssessmentId,
            PracticalAttemptStatus status);

    Page<PracticalAttempt> findAllByUserIdOrderByStartedAtDesc(UUID userId, Pageable pageable);

    // Delete guard — mirrors QuestionBankService.delete's "never touched" check.
    boolean existsByPracticalAssessmentId(UUID practicalAssessmentId);

    @Query("select a from PracticalAttempt a join fetch a.practicalAssessment join fetch a.user where a.status = :status order by a.submittedAt asc")
    Page<PracticalAttempt> findAllByStatusOrderBySubmittedAtAsc(@Param("status") PracticalAttemptStatus status, Pageable pageable);

    // Latest EVALUATED attempt for a given (user, skill) pair — backs the practical-evidence
    // endpoint. Bounded with Pageable(0, 1) for a real LIMIT 1, same discipline as
    // AssessmentRepository.findLatestByUserAndSkillAndStatusIn.
    @Query("""
            select a from PracticalAttempt a join fetch a.practicalAssessment pa
            where a.user.id = :userId and pa.skill.id = :skillId and a.status = com.studen.practical.PracticalAttemptStatus.EVALUATED
            order by a.evaluatedAt desc
            """)
    List<PracticalAttempt> findLatestEvaluatedByUserAndSkill(@Param("userId") UUID userId, @Param("skillId") UUID skillId,
            Pageable pageable);

    // Atomic compare-and-set shared by the deadline-expiry check and submit() — mirrors
    // AssessmentRepository.finalizeIfInProgress. A 0-row update (lost a race) is not an error:
    // every caller re-fetches and trusts whatever is actually stored.
    @Modifying(clearAutomatically = true)
    @Query("""
            update PracticalAttempt a
            set a.status = :status, a.submittedAt = :submittedAt
            where a.id = :id and a.status = com.studen.practical.PracticalAttemptStatus.IN_PROGRESS
            """)
    int transitionIfInProgress(@Param("id") UUID id, @Param("status") PracticalAttemptStatus status,
            @Param("submittedAt") Instant submittedAt);
}

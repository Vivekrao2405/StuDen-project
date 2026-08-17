package com.studen.practical;

import com.studen.questionbank.Difficulty;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PracticalAssessmentRepository extends JpaRepository<PracticalAssessment, UUID> {

    // Shared by both the admin list (status usually null = "any") and the student list (status
    // always PUBLISHED) — same empty-string sentinel trick as QuestionRepository.search, for the
    // same reason (a bound null breaks Postgres's type resolution inside lower(concat(...))).
    @Query("""
            select pa from PracticalAssessment pa
            where (:skillId is null or pa.skill.id = :skillId)
              and (:practicalType is null or pa.practicalType = :practicalType)
              and (:difficulty is null or pa.difficulty = :difficulty)
              and (:status is null or pa.status = :status)
              and (:search = '' or lower(pa.title) like lower(concat('%', :search, '%')))
            """)
    Page<PracticalAssessment> search(@Param("skillId") UUID skillId, @Param("practicalType") PracticalType practicalType,
            @Param("difficulty") Difficulty difficulty, @Param("status") PracticalAssessmentStatus status,
            @Param("search") String search, Pageable pageable);

    // Student-facing single lookup — PUBLISHED only, so a DRAFT/REVIEW/ARCHIVED id 404s the same
    // way a nonexistent one would (spec §23: "only PUBLISHED assessments are visible").
    java.util.Optional<PracticalAssessment> findByIdAndStatus(UUID id, PracticalAssessmentStatus status);
}

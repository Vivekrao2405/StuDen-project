package com.studen.assessment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssessmentQuestionRepository extends JpaRepository<AssessmentQuestion, UUID> {

    @Query("""
            select aq from AssessmentQuestion aq join fetch aq.question q
            where aq.assessment.id = :assessmentId order by aq.displayOrder asc
            """)
    List<AssessmentQuestion> findAllByAssessmentIdOrderByDisplayOrderAsc(@Param("assessmentId") UUID assessmentId);

    // IDOR guard for the autosave endpoint: a questionId that belongs to a different assessment
    // (even one owned by the same student) must not resolve here.
    @Query("""
            select aq from AssessmentQuestion aq join fetch aq.question q
            where aq.id = :id and aq.assessment.id = :assessmentId
            """)
    Optional<AssessmentQuestion> findByIdAndAssessmentId(@Param("id") UUID id, @Param("assessmentId") UUID assessmentId);
}

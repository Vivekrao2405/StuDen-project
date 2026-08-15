package com.studen.assessment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssessmentAnswerRepository extends JpaRepository<AssessmentAnswer, UUID> {

    Optional<AssessmentAnswer> findByAssessmentQuestionId(UUID assessmentQuestionId);

    @Query("select aa from AssessmentAnswer aa where aa.assessment.id = :assessmentId")
    List<AssessmentAnswer> findAllByAssessmentId(@Param("assessmentId") UUID assessmentId);
}

package com.studen.assessment;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssessmentQuestionOptionRepository extends JpaRepository<AssessmentQuestionOption, UUID> {

    // Batch-fetch across many assessment questions in one query — same idiom as
    // QuestionOptionRepository.findAllByQuestionIdInOrderByDisplayOrderAsc, avoids an N+1 when
    // rendering a whole assessment's worth of questions at once.
    @Query("""
            select aqo from AssessmentQuestionOption aqo join fetch aqo.questionOption
            where aqo.assessmentQuestion.id in :assessmentQuestionIds order by aqo.displayOrder asc
            """)
    List<AssessmentQuestionOption> findAllByAssessmentQuestionIdInOrderByDisplayOrderAsc(
            @Param("assessmentQuestionIds") List<UUID> assessmentQuestionIds);

    @Query("""
            select aqo from AssessmentQuestionOption aqo join fetch aqo.questionOption
            where aqo.assessmentQuestion.id = :assessmentQuestionId order by aqo.displayOrder asc
            """)
    List<AssessmentQuestionOption> findAllByAssessmentQuestionIdOrderByDisplayOrderAsc(
            @Param("assessmentQuestionId") UUID assessmentQuestionId);
}

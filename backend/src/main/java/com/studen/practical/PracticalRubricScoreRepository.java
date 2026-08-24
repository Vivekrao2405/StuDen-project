package com.studen.practical;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PracticalRubricScoreRepository extends JpaRepository<PracticalRubricScore, UUID> {

    List<PracticalRubricScore> findAllByPracticalAttemptId(UUID practicalAttemptId);

    // Rubric criteria are per-question (PracticalRubricCriterion.practicalQuestion) — this filters
    // one attempt's scores down to just the criteria belonging to one question, without needing a
    // separate practical_attempt_question_id column on this table (derived via the criterion's own
    // question FK instead).
    @Query("select s from PracticalRubricScore s where s.practicalAttempt.id = :attemptId "
            + "and s.rubricCriterion.practicalQuestion.id = :questionId")
    List<PracticalRubricScore> findAllByAttemptIdAndQuestionId(@Param("attemptId") UUID attemptId,
            @Param("questionId") UUID questionId);
}

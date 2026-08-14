package com.studen.questionbank;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionOptionRepository extends JpaRepository<QuestionOption, UUID> {

    // Batch-fetch across many questions in one query — same pattern as
    // ServiceMediaRepository.findAllByServiceIdInOrderByDisplayOrderAsc, avoids an N+1 when
    // rendering a list of questions with their options (e.g. the learner API).
    List<QuestionOption> findAllByQuestionIdInOrderByDisplayOrderAsc(List<UUID> questionIds);

    List<QuestionOption> findAllByQuestionIdOrderByDisplayOrderAsc(UUID questionId);
}

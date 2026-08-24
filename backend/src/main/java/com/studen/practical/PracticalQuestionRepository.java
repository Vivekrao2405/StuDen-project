package com.studen.practical;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PracticalQuestionRepository extends JpaRepository<PracticalQuestion, UUID> {

    List<PracticalQuestion> findAllByPracticalAssessmentIdOrderByDisplayOrderAsc(UUID practicalAssessmentId);

    void deleteAllByPracticalAssessmentId(UUID practicalAssessmentId);

    // Batched question-count-per-assessment for the admin list view — avoids one count query per
    // row (N+1) the same way the Showcase perf pass (see project memory) fixed the same shape of
    // problem there.
    @Query("select pq.practicalAssessment.id, count(pq) from PracticalQuestion pq "
            + "where pq.practicalAssessment.id in :assessmentIds group by pq.practicalAssessment.id")
    List<Object[]> countByAssessmentIds(@Param("assessmentIds") List<UUID> assessmentIds);
}

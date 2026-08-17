package com.studen.practical;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PracticalRubricCriterionRepository extends JpaRepository<PracticalRubricCriterion, UUID> {

    List<PracticalRubricCriterion> findAllByPracticalAssessmentIdOrderByDisplayOrderAsc(UUID practicalAssessmentId);

    void deleteAllByPracticalAssessmentId(UUID practicalAssessmentId);
}

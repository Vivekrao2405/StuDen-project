package com.studen.practical;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PracticalRubricScoreRepository extends JpaRepository<PracticalRubricScore, UUID> {

    List<PracticalRubricScore> findAllByPracticalAttemptId(UUID practicalAttemptId);
}

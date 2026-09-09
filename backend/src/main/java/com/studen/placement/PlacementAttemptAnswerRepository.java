package com.studen.placement;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlacementAttemptAnswerRepository extends JpaRepository<PlacementAttemptAnswer, UUID> {

    Optional<PlacementAttemptAnswer> findByAttemptQuestionId(UUID attemptQuestionId);

    @Query("select a from PlacementAttemptAnswer a where a.attempt.id = :attemptId")
    List<PlacementAttemptAnswer> findAllByAttemptId(@Param("attemptId") UUID attemptId);
}

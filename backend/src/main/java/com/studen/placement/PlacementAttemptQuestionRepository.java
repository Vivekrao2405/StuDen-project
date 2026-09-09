package com.studen.placement;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlacementAttemptQuestionRepository extends JpaRepository<PlacementAttemptQuestion, UUID> {

    @Query("""
            select q from PlacementAttemptQuestion q
            join fetch q.question qq join fetch qq.skill
            where q.attempt.id = :attemptId
            order by q.displayOrder asc
            """)
    List<PlacementAttemptQuestion> findAllByAttemptIdOrderByDisplayOrderAsc(@Param("attemptId") UUID attemptId);

    @Query("""
            select q from PlacementAttemptQuestion q join fetch q.question
            where q.id = :id and q.attempt.id = :attemptId
            """)
    Optional<PlacementAttemptQuestion> findByIdAndAttemptId(@Param("id") UUID id, @Param("attemptId") UUID attemptId);
}

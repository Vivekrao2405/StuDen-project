package com.studen.placement;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlacementAttemptQuestionRepository extends JpaRepository<PlacementAttemptQuestion, UUID> {

    // left join, not inner: a PRACTICAL_ASSESSMENT slot has a null question, and a QUESTION slot
    // has a null practicalAssessment — an inner join on either would silently drop the other kind.
    @Query("""
            select q from PlacementAttemptQuestion q
            left join fetch q.question
            left join fetch q.practicalAssessment
            left join fetch q.practicalAttempt
            where q.attempt.id = :attemptId
            order by q.displayOrder asc
            """)
    List<PlacementAttemptQuestion> findAllByAttemptIdOrderByDisplayOrderAsc(@Param("attemptId") UUID attemptId);

    @Query("""
            select q from PlacementAttemptQuestion q
            left join fetch q.question
            left join fetch q.practicalAssessment
            left join fetch q.practicalAttempt
            where q.id = :id and q.attempt.id = :attemptId
            """)
    Optional<PlacementAttemptQuestion> findByIdAndAttemptId(@Param("id") UUID id, @Param("attemptId") UUID attemptId);
}

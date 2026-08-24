package com.studen.practical;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PracticalAttemptQuestionRepository extends JpaRepository<PracticalAttemptQuestion, UUID> {

    List<PracticalAttemptQuestion> findAllByPracticalAttemptIdOrderByDisplayOrderAsc(UUID practicalAttemptId);

    // IDOR guard for every learner-facing per-question lookup (run/save) -- mirrors
    // PracticalAttemptRepository.findByIdAndUserId's join-through-owner pattern, one hop deeper.
    @Query("""
            select aq from PracticalAttemptQuestion aq
            join aq.practicalAttempt a
            where aq.id = :id and a.id = :attemptId and a.user.id = :userId
            """)
    Optional<PracticalAttemptQuestion> findByIdAndAttemptIdAndUserId(@Param("id") UUID id,
            @Param("attemptId") UUID attemptId, @Param("userId") UUID userId);
}

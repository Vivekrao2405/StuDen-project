package com.studen.practical.execution;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExecutionJobRepository extends JpaRepository<ExecutionJob, UUID> {

    // Run history: every execution for an attempt, oldest first (Run #1, #2, #3...).
    List<ExecutionJob> findAllByPracticalAttemptIdOrderByCreatedAtAsc(UUID practicalAttemptId);

    // IDOR guard for the learner-facing executions endpoint -- mirrors
    // PracticalAttemptRepository.findByIdAndUserId's join-through-owner pattern.
    @Query("""
            select j from ExecutionJob j join j.practicalAttempt a
            where j.id = :id and a.user.id = :userId
            """)
    Optional<ExecutionJob> findByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);
}

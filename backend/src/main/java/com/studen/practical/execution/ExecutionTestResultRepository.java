package com.studen.practical.execution;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExecutionTestResultRepository extends JpaRepository<ExecutionTestResult, UUID> {

    List<ExecutionTestResult> findAllByExecutionJobIdOrderByCreatedAtAsc(UUID executionJobId);
}

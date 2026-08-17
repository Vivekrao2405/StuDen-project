package com.studen.practical.execution;

import com.studen.common.entity.BaseEntity;
import com.studen.practical.PracticalTestCase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One test case's outcome within an {@link ExecutionJob}. {@code hidden} is denormalized from
 * {@link PracticalTestCase} at execution time -- a later admin edit toggling a test's hidden flag
 * must never retroactively change what a historical run is allowed to reveal. Every DTO mapping
 * must gate {@code actualOutput} on this field, never the live {@code PracticalTestCase.hidden}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "execution_test_results")
public class ExecutionTestResult extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "execution_job_id", nullable = false)
    private ExecutionJob executionJob;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "test_case_id", nullable = false)
    private PracticalTestCase testCase;

    @Column(nullable = false)
    private boolean hidden;

    @Column(nullable = false)
    private boolean passed;

    @Column(name = "actual_output", columnDefinition = "TEXT")
    private String actualOutput;

    @Column(name = "execution_time_ms")
    private Long executionTimeMs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TestOutcomeStatus status;

    public ExecutionTestResult(ExecutionJob executionJob, PracticalTestCase testCase, boolean hidden, boolean passed,
            String actualOutput, Long executionTimeMs, TestOutcomeStatus status) {
        this.executionJob = executionJob;
        this.testCase = testCase;
        this.hidden = hidden;
        this.passed = passed;
        this.actualOutput = actualOutput;
        this.executionTimeMs = executionTimeMs;
        this.status = status;
    }
}

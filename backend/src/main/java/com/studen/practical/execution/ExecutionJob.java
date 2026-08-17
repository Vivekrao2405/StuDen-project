package com.studen.practical.execution;

import com.studen.common.entity.BaseEntity;
import com.studen.practical.CodingLanguage;
import com.studen.practical.PracticalAttempt;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One Run click or one final Submit's execution -- the run-history/audit trail behind
 * {@code com.studen.practical.PracticalAttemptService}. {@code sourceCode} is a snapshot taken at
 * the moment execution started, independent of {@code PracticalAttempt.submissionContent} (which
 * keeps autosaving afterward), so historical runs stay accurate. Never own scoring policy --
 * carries only factual execution results; the attempt/evaluation layer decides what they mean.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "execution_jobs")
public class ExecutionJob extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "practical_attempt_id", nullable = false)
    private PracticalAttempt practicalAttempt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExecutionJobKind kind;

    @Enumerated(EnumType.STRING)
    private CodingLanguage language;

    @Column(name = "source_code", nullable = false, columnDefinition = "TEXT")
    private String sourceCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExecutionJobStatus status = ExecutionJobStatus.QUEUED;

    @Column(name = "tests_passed")
    private Integer testsPassed;

    @Column(name = "tests_total")
    private Integer testsTotal;

    @Column(name = "compile_error", columnDefinition = "TEXT")
    private String compileError;

    @Column(name = "runtime_error", columnDefinition = "TEXT")
    private String runtimeError;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public ExecutionJob(PracticalAttempt practicalAttempt, ExecutionJobKind kind, CodingLanguage language, String sourceCode) {
        this.practicalAttempt = practicalAttempt;
        this.kind = kind;
        this.language = language;
        this.sourceCode = sourceCode;
    }
}

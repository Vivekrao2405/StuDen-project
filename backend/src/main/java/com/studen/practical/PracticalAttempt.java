package com.studen.practical;

import com.studen.common.entity.BaseEntity;
import com.studen.user.User;
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
 * One student's attempt at a {@link PracticalAssessment}. {@code deadline} is computed once at
 * start ({@code startedAt + assessment.timeLimitMinutes}) and is the sole source of truth for
 * expiry — the frontend only ever displays a countdown derived from it, exactly like
 * {@code com.studen.assessment.Assessment.timeLimitSeconds} (spec §27).
 *
 * <p>{@code submissionContent} holds the primary text payload (source code for CODING, a JSON
 * {@code {html,css,js}} blob for WEB_DEVELOPMENT, the query text for SQL, a free-text explanation
 * otherwise); {@code submissionFileUrl}/{@code submissionLinkUrl} cover UI/UX and generic file/
 * link submissions. v1 policy: once SUBMITTED, an attempt cannot be edited or resubmitted — the
 * spec allows resubmission only "where the assessment explicitly allows it," which isn't built
 * this phase.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "practical_attempts")
public class PracticalAttempt extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "practical_assessment_id", nullable = false)
    private PracticalAssessment practicalAssessment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PracticalAttemptStatus status = PracticalAttemptStatus.IN_PROGRESS;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(nullable = false)
    private Instant deadline;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "evaluated_at")
    private Instant evaluatedAt;

    // Set once, the first time any ExecutionJob for this attempt reaches a post-compile status.
    // Feeds Phase 7.6's Assessment Integrity system later; nothing in this phase reads it back.
    @Column(name = "first_successful_compilation_at")
    private Instant firstSuccessfulCompilationAt;

    private Integer score;

    @Column(name = "max_score")
    private Integer maxScore;

    @Column(columnDefinition = "TEXT")
    private String feedback;

    @Enumerated(EnumType.STRING)
    @Column(name = "selected_language")
    private CodingLanguage selectedLanguage;

    @Column(name = "submission_content", columnDefinition = "TEXT")
    private String submissionContent;

    @Column(name = "submission_file_url")
    private String submissionFileUrl;

    @Column(name = "submission_link_url")
    private String submissionLinkUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evaluated_by")
    private User evaluatedBy;

    // Phase 7.6 Assessment Integrity -- cached summary, recomputed deterministically from
    // assessment_integrity_events by com.studen.integrity.IntegrityScoringService.recompute on
    // every event write. Entirely separate from score/maxScore above -- never read by this
    // attempt's grading path (PracticalAttemptService), only by the integrity feature itself.
    // Default to a clean baseline so an attempt with zero integrity activity (recompute never
    // ran) still reports CLEAN/100 rather than null -- IntegrityScoringService overwrites these
    // the moment any event is recorded.
    @Column(name = "integrity_score")
    private Integer integrityScore = 100;

    @Enumerated(EnumType.STRING)
    @Column(name = "integrity_status")
    private com.studen.integrity.IntegrityStatus integrityStatus = com.studen.integrity.IntegrityStatus.CLEAN;

    @Column(name = "tab_switch_count", nullable = false)
    private int tabSwitchCount = 0;

    @Column(name = "copy_attempt_count", nullable = false)
    private int copyAttemptCount = 0;

    @Column(name = "paste_attempt_count", nullable = false)
    private int pasteAttemptCount = 0;

    @Column(name = "cut_attempt_count", nullable = false)
    private int cutAttemptCount = 0;

    @Column(name = "fullscreen_exit_count", nullable = false)
    private int fullscreenExitCount = 0;

    @Column(name = "multiple_session_count", nullable = false)
    private int multipleSessionCount = 0;

    @Column(name = "total_integrity_event_count", nullable = false)
    private int totalIntegrityEventCount = 0;

    @Column(name = "suspicious_event_count", nullable = false)
    private int suspiciousEventCount = 0;

    @Column(name = "critical_event_count", nullable = false)
    private int criticalEventCount = 0;

    // Manual admin review override -- set only via AdminIntegrityService.override, never
    // automatically. The *effective* status shown everywhere is integrityOverrideStatus if
    // present, else integrityStatus above.
    @Enumerated(EnumType.STRING)
    @Column(name = "integrity_override_status")
    private com.studen.integrity.IntegrityStatus integrityOverrideStatus;

    @Column(name = "integrity_override_reason", columnDefinition = "TEXT")
    private String integrityOverrideReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "integrity_override_by")
    private User integrityOverrideBy;

    @Column(name = "integrity_overridden_at")
    private Instant integrityOverriddenAt;

    public PracticalAttempt(PracticalAssessment practicalAssessment, User user, Instant startedAt, Instant deadline,
            Integer maxScore) {
        this.practicalAssessment = practicalAssessment;
        this.user = user;
        this.startedAt = startedAt;
        this.deadline = deadline;
        this.maxScore = maxScore;
    }
}

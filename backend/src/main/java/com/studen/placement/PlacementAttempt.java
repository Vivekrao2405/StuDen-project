package com.studen.placement;

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
 * One student attempt at a {@link PlacementAssessment}. Foundation only: Phase 0 persists the
 * shape (who, which assessment, status, timing, result) and adds no scoring logic and no
 * endpoints. Phase 3 owns the engine that starts, times out and scores these rows.
 *
 * <p>{@code score}/{@code maxScore}/{@code scorePercentage} stay null until the attempt reaches a
 * terminal status, matching how {@code com.studen.assessment.Assessment} already behaves.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "placement_attempts")
public class PlacementAttempt extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "placement_assessment_id", nullable = false)
    private PlacementAssessment placementAssessment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlacementAttemptStatus status = PlacementAttemptStatus.IN_PROGRESS;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    private Integer score;

    @Column(name = "max_score")
    private Integer maxScore;

    @Column(name = "score_percentage")
    private Integer scorePercentage;

    public PlacementAttempt(User student, PlacementAssessment placementAssessment, Instant startedAt) {
        this.student = student;
        this.placementAssessment = placementAssessment;
        this.startedAt = startedAt;
    }
}

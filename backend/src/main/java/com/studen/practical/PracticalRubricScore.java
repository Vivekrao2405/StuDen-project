package com.studen.practical;

import com.studen.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Admin-awarded points for one {@link PracticalRubricCriterion} on one {@link PracticalAttempt}.
 * {@code PracticalAttempt.score} is always {@code sum(pointsAwarded)} across these rows, computed
 * server-side at evaluation time — the frontend never sends (and the backend never trusts) a
 * total (spec §22).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "practical_rubric_scores")
public class PracticalRubricScore extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "practical_attempt_id", nullable = false)
    private PracticalAttempt practicalAttempt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rubric_criterion_id", nullable = false)
    private PracticalRubricCriterion rubricCriterion;

    @Column(name = "points_awarded", nullable = false)
    private int pointsAwarded;

    public PracticalRubricScore(PracticalAttempt practicalAttempt, PracticalRubricCriterion rubricCriterion,
            int pointsAwarded) {
        this.practicalAttempt = practicalAttempt;
        this.rubricCriterion = rubricCriterion;
        this.pointsAwarded = pointsAwarded;
    }
}

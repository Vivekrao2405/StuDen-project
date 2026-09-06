package com.studen.placement;

import com.studen.common.entity.BaseEntity;
import com.studen.skill.Skill;
import com.studen.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One measured (student, skill, score) data point, plus the {@link PlacementAttempt} that produced
 * it. This is the row later phases read to derive Overall Placement Readiness and Skill Gaps — the
 * frontend must never compute readiness from hard-coded skill knowledge of its own.
 *
 * <p>Deliberately a history rather than one current value per skill: the product loop is
 * Assess -> Learn -> Practice -> Reassess, so improvement over time has to stay visible. The
 * newest row for a (student, skill) pair is the current score; the index on
 * (student_id, skill_id, recorded_at DESC) makes that lookup cheap.
 *
 * <p>{@code attempt} is nullable and set to null rather than cascading if an attempt is ever
 * purged, so a recorded measurement is never destroyed as a side effect.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "placement_skill_scores")
public class PlacementSkillScore extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_id")
    private PlacementAttempt attempt;

    @Column(name = "score_percentage", nullable = false)
    private int scorePercentage;

    @Column(name = "correct_count")
    private Integer correctCount;

    @Column(name = "total_questions")
    private Integer totalQuestions;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    public PlacementSkillScore(User student, Skill skill, PlacementAttempt attempt, int scorePercentage,
            Instant recordedAt) {
        this.student = student;
        this.skill = skill;
        this.attempt = attempt;
        this.scorePercentage = scorePercentage;
        this.recordedAt = recordedAt;
    }
}

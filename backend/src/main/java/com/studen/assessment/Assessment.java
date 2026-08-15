package com.studen.assessment;

import com.studen.common.entity.BaseEntity;
import com.studen.skill.Skill;
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
 * One generated assessment instance for one student on one skill. {@code totalQuestions} is
 * frozen at generation time from the trusted backend config ({@code AssessmentProperties}), never
 * client-supplied. {@code correctCount}/{@code scorePercentage} stay null until the assessment
 * reaches a terminal status ({@link AssessmentStatus#SUBMITTED}/{@link AssessmentStatus#EXPIRED}).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "assessments")
public class Assessment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    @Enumerated(EnumType.STRING)
    @Column(name = "assessment_type", nullable = false)
    private AssessmentType assessmentType = AssessmentType.KNOWLEDGE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssessmentStatus status = AssessmentStatus.IN_PROGRESS;

    @Column(name = "total_questions", nullable = false)
    private int totalQuestions;

    @Column(name = "correct_count")
    private Integer correctCount;

    @Column(name = "score_percentage")
    private Integer scorePercentage;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    // Null = untimed. Backend-authoritative: the frontend only ever displays a countdown derived
    // from (startedAt + timeLimitSeconds) - now, recomputed from this server timestamp on every
    // fetch — see AssessmentService.remainingSeconds.
    @Column(name = "time_limit_seconds")
    private Integer timeLimitSeconds;

    public Assessment(User user, Skill skill, AssessmentType assessmentType, int totalQuestions,
            Integer timeLimitSeconds) {
        this.user = user;
        this.skill = skill;
        this.assessmentType = assessmentType;
        this.totalQuestions = totalQuestions;
        this.timeLimitSeconds = timeLimitSeconds;
        this.startedAt = Instant.now();
    }
}

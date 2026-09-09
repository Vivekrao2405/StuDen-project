package com.studen.placement;

import com.studen.common.entity.BaseEntity;
import com.studen.questionbank.Question;
import com.studen.skill.Skill;
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
 * One question slot within a specific {@link PlacementAttempt}, snapshotted from the
 * {@link PlacementAssessment}'s configured {@link PlacementAssessmentQuestion} links at
 * {@code startOrResume} time — never read live from that shared, admin-editable link table again.
 *
 * <p>{@code skillId}/{@code skillName} ARE a deliberate snapshot (the {@link Question} row itself
 * is immutable once PUBLISHED, per Question Bank versioning, so referencing it live is safe — but
 * {@code Skill.name} is not version-locked). Without this snapshot, an admin renaming a skill, or
 * editing an assessment's question set after a student has already attempted it, would silently
 * rewrite a historical attempt's skill breakdown — exactly the failure mode
 * {@code com.studen.assessment.AssessmentQuestion}'s topic/tag snapshot already guards against.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "placement_attempt_questions")
public class PlacementAttemptQuestion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "placement_attempt_id", nullable = false)
    private PlacementAttempt attempt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    @Column(name = "skill_name", nullable = false)
    private String skillName;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private int points = 1;

    public PlacementAttemptQuestion(PlacementAttempt attempt, Question question, Skill skill, String skillName,
            int displayOrder, int points) {
        this.attempt = attempt;
        this.question = question;
        this.skill = skill;
        this.skillName = skillName;
        this.displayOrder = displayOrder;
        this.points = points;
    }
}

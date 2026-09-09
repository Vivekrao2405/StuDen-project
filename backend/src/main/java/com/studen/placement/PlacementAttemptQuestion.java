package com.studen.placement;

import com.studen.common.entity.BaseEntity;
import com.studen.practical.PracticalAssessment;
import com.studen.practical.PracticalAttempt;
import com.studen.questionbank.Question;
import com.studen.skill.Skill;
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
 * One question/practical-task slot within a specific {@link PlacementAttempt}, snapshotted from
 * the {@link PlacementAssessment}'s configured {@link PlacementAssessmentQuestion} links at
 * {@code startOrResume} time — never read live from that shared, admin-editable link table again.
 *
 * <p>{@code skillId}/{@code skillName} ARE a deliberate snapshot (both {@link Question} and
 * {@link PracticalAssessment} are immutable once PUBLISHED, per their respective versioning, so
 * referencing either live is safe — but {@code Skill.name} is not version-locked). Without this
 * snapshot, an admin renaming a skill, or editing an assessment's item set after a student has
 * already attempted it, would silently rewrite a historical attempt's skill breakdown — exactly
 * the failure mode {@code com.studen.assessment.AssessmentQuestion}'s topic/tag snapshot already
 * guards against.
 *
 * <p>A PRACTICAL_ASSESSMENT slot delegates entirely to the existing
 * {@code com.studen.practical.PracticalAttemptService} engine — {@code practicalAttempt} is the
 * live link to the real {@link PracticalAttempt} this slot started/resumed, so its own take/run/
 * submit/scoring never gets duplicated here.
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

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false)
    private ModuleItemType itemType = ModuleItemType.QUESTION;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id")
    private Question question;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "practical_assessment_id")
    private PracticalAssessment practicalAssessment;

    // Set once startOrResume has started/resumed the underlying PracticalAttempt for this slot —
    // null until then, and always null for a QUESTION slot.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "practical_attempt_id")
    private PracticalAttempt practicalAttempt;

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
        this.itemType = ModuleItemType.QUESTION;
        this.question = question;
        this.skill = skill;
        this.skillName = skillName;
        this.displayOrder = displayOrder;
        this.points = points;
    }

    public PlacementAttemptQuestion(PlacementAttempt attempt, PracticalAssessment practicalAssessment, Skill skill,
            String skillName, int displayOrder, int points) {
        this.attempt = attempt;
        this.itemType = ModuleItemType.PRACTICAL_ASSESSMENT;
        this.practicalAssessment = practicalAssessment;
        this.skill = skill;
        this.skillName = skillName;
        this.displayOrder = displayOrder;
        this.points = points;
    }
}

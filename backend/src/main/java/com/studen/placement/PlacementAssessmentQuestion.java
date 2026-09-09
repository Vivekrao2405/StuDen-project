package com.studen.placement;

import com.studen.common.entity.BaseEntity;
import com.studen.practical.PracticalAssessment;
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
 * Links a {@link PlacementAssessment} to one piece of shared content it evaluates — either a
 * Question Bank {@link Question} (MCQ/aptitude/technical/case-study) or an existing
 * {@link PracticalAssessment} (coding/SQL/web/etc, reusing {@code com.studen.practical}'s engine
 * and execution sandbox unchanged). Exactly one of {@code question}/{@code practicalAssessment} is
 * ever set, discriminated by {@code itemType} — the same typed-nullable-FK pattern
 * {@code PlacementModuleItem} already uses for an identical "one of several existing content
 * kinds" problem (V31/V35), rather than a second coding/assessment system.
 *
 * <p>The linked content stays shared catalog data: it is never owned or copied by the assessment,
 * one question/practical can appear in many assessments, and deleting an assessment removes only
 * these link rows.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "placement_assessment_questions")
public class PlacementAssessmentQuestion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "placement_assessment_id", nullable = false)
    private PlacementAssessment assessment;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false)
    private ModuleItemType itemType = ModuleItemType.QUESTION;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id")
    private Question question;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "practical_assessment_id")
    private PracticalAssessment practicalAssessment;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(nullable = false)
    private int points = 1;

    public PlacementAssessmentQuestion(PlacementAssessment assessment, Question question, int displayOrder,
            int points) {
        this.assessment = assessment;
        this.itemType = ModuleItemType.QUESTION;
        this.question = question;
        this.displayOrder = displayOrder;
        this.points = points;
    }

    public PlacementAssessmentQuestion(PlacementAssessment assessment, PracticalAssessment practicalAssessment,
            int displayOrder, int points) {
        this.assessment = assessment;
        this.itemType = ModuleItemType.PRACTICAL_ASSESSMENT;
        this.practicalAssessment = practicalAssessment;
        this.displayOrder = displayOrder;
        this.points = points;
    }

    // The skill this item evaluates, regardless of which content kind it links to — the single
    // place both admin-facing responses and attempt-snapshotting read from.
    public Skill resolveSkill() {
        return itemType == ModuleItemType.QUESTION ? question.getSkill() : practicalAssessment.getSkill();
    }
}

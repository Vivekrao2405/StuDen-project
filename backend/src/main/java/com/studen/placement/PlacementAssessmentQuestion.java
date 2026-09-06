package com.studen.placement;

import com.studen.common.entity.BaseEntity;
import com.studen.questionbank.Question;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Links a {@link PlacementAssessment} to a Question Bank {@link Question}, carrying the
 * assessment-specific ordering and point value.
 *
 * <p>The question stays shared catalog content: it is never owned or copied by the assessment, one
 * question can appear in many assessments, and deleting an assessment removes only these link rows.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "placement_assessment_questions",
        uniqueConstraints = @UniqueConstraint(name = "uq_placement_assessment_question",
                columnNames = {"placement_assessment_id", "question_id"}))
public class PlacementAssessmentQuestion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "placement_assessment_id", nullable = false)
    private PlacementAssessment assessment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(nullable = false)
    private int points = 1;

    public PlacementAssessmentQuestion(PlacementAssessment assessment, Question question, int displayOrder,
            int points) {
        this.assessment = assessment;
        this.question = question;
        this.displayOrder = displayOrder;
        this.points = points;
    }
}

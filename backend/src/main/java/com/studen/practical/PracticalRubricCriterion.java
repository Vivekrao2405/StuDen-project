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
 * One scored criterion for a MANUAL/HYBRID-evaluated {@link PracticalQuestion} (e.g. "Visual
 * hierarchy — 20 pts"). {@code AdminPracticalAssessmentService} requires every question's
 * criteria to sum to 100 at publish time; the actual awarded score always comes from
 * {@link PracticalRubricScore} rows written at evaluation time, never a client-supplied total
 * (spec §22).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "practical_rubric_criteria")
public class PracticalRubricCriterion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "practical_question_id", nullable = false)
    private PracticalQuestion practicalQuestion;

    @Column(nullable = false)
    private String criterion;

    @Column(name = "max_points", nullable = false)
    private int maxPoints;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    public PracticalRubricCriterion(PracticalQuestion practicalQuestion, String criterion, int maxPoints,
            int displayOrder) {
        this.practicalQuestion = practicalQuestion;
        this.criterion = criterion;
        this.maxPoints = maxPoints;
        this.displayOrder = displayOrder;
    }
}

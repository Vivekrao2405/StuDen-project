package com.studen.assessment;

import com.studen.common.entity.BaseEntity;
import com.studen.questionbank.Question;
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
 * One question slot within a generated {@link Assessment}. Points at the PUBLISHED
 * {@link Question} row selected at generation time — never copied/snapshotted, since Phase 7.1's
 * versioning already forbids mutating a PUBLISHED question in place (edits fork a new row).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "assessment_questions")
public class AssessmentQuestion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_id", nullable = false)
    private Assessment assessment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private int points = 1;

    public AssessmentQuestion(Assessment assessment, Question question, int displayOrder) {
        this.assessment = assessment;
        this.question = question;
        this.displayOrder = displayOrder;
    }
}

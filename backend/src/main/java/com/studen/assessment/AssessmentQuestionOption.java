package com.studen.assessment;

import com.studen.common.entity.BaseEntity;
import com.studen.questionbank.QuestionOption;
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
 * The per-instance randomized option order for one {@link AssessmentQuestion} — and, just as
 * importantly, the authoritative set of option IDs that are legal to submit for it. Answer
 * validation checks membership here, never against {@code Question}'s live option list directly,
 * so an option ID from a different question (or a stale/unknown ID) is a plain existence check
 * away from rejection.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "assessment_question_options")
public class AssessmentQuestionOption extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_question_id", nullable = false)
    private AssessmentQuestion assessmentQuestion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_option_id", nullable = false)
    private QuestionOption questionOption;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    public AssessmentQuestionOption(AssessmentQuestion assessmentQuestion, QuestionOption questionOption,
            int displayOrder) {
        this.assessmentQuestion = assessmentQuestion;
        this.questionOption = questionOption;
        this.displayOrder = displayOrder;
    }
}

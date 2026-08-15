package com.studen.assessment;

import com.studen.common.entity.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One student's answer to one {@link AssessmentQuestion} — one row per question, upserted on every
 * autosave call. {@code selectedOptionIds} holds exactly one ID for MCQ_SINGLE/TRUE_FALSE, one or
 * more for MCQ_MULTIPLE (cardinality enforced in {@code AssessmentService}, not here).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "assessment_answers")
public class AssessmentAnswer extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_id", nullable = false)
    private Assessment assessment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_question_id", nullable = false)
    private AssessmentQuestion assessmentQuestion;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "assessment_answer_selected_options", joinColumns = @JoinColumn(name = "assessment_answer_id"))
    @Column(name = "option_id")
    private Set<UUID> selectedOptionIds = new LinkedHashSet<>();

    @Column(name = "answered_at", nullable = false)
    private Instant answeredAt = Instant.now();

    public AssessmentAnswer(Assessment assessment, AssessmentQuestion assessmentQuestion) {
        this.assessment = assessment;
        this.assessmentQuestion = assessmentQuestion;
    }
}

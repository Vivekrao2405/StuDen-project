package com.studen.placement;

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
 * One student's answer to one {@link PlacementAttemptQuestion} — one row per question, upserted on
 * every save. Mirrors {@code com.studen.assessment.AssessmentAnswer} exactly, including the
 * exact-set-equality correctness rule applied against it in {@code PlacementAttemptService}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "placement_attempt_answers")
public class PlacementAttemptAnswer extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "placement_attempt_id", nullable = false)
    private PlacementAttempt attempt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "placement_attempt_question_id", nullable = false)
    private PlacementAttemptQuestion attemptQuestion;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "placement_attempt_answer_selected_options",
            joinColumns = @JoinColumn(name = "placement_attempt_answer_id"))
    @Column(name = "option_id")
    private Set<UUID> selectedOptionIds = new LinkedHashSet<>();

    @Column(name = "answered_at", nullable = false)
    private Instant answeredAt = Instant.now();

    public PlacementAttemptAnswer(PlacementAttempt attempt, PlacementAttemptQuestion attemptQuestion) {
        this.attempt = attempt;
        this.attemptQuestion = attemptQuestion;
    }
}

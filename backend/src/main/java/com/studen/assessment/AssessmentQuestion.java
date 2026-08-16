package com.studen.assessment;

import com.studen.common.entity.BaseEntity;
import com.studen.questionbank.Question;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One question slot within a generated {@link Assessment}. Points at the PUBLISHED
 * {@link Question} row selected at generation time — never copied/snapshotted, since Phase 7.1's
 * versioning already forbids mutating a PUBLISHED question in place (edits fork a new row).
 *
 * <p>{@code topicId}/{@code topicName} ARE a deliberate snapshot, unlike the rest of this entity —
 * taken at generation time in {@code AssessmentService.startOrResume}. Unlike {@link Question}
 * itself, a {@link com.studen.questionbank.Topic} row is not version-locked once referenced, so
 * without this snapshot a future topic rename would silently rewrite Phase 7.3 topic-performance
 * history for every past assessment that used it.
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

    @Column(name = "topic_id")
    private UUID topicId;

    @Column(name = "topic_name")
    private String topicName;

    public AssessmentQuestion(Assessment assessment, Question question, int displayOrder) {
        this.assessment = assessment;
        this.question = question;
        this.displayOrder = displayOrder;
    }

    public AssessmentQuestion(Assessment assessment, Question question, int displayOrder, UUID topicId,
            String topicName) {
        this(assessment, question, displayOrder);
        this.topicId = topicId;
        this.topicName = topicName;
    }
}

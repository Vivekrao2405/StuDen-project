package com.studen.practical;

import com.studen.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One question's submission/state/score within one {@link PracticalAttempt} — created once, at
 * {@code startOrResume}, for every {@link PracticalQuestion} on the assessment (spec §16: "Capture
 * the final code for every question"). Before this phase, a {@code PracticalAttempt} held exactly
 * this data directly on itself (submissionContent/selectedLanguage/score/etc); those columns still
 * exist there for historical rows, but {@code PracticalAttempt.score}/{@code maxScore} now mean the
 * attempt-wide TOTAL — {@code sum(pointsEarned)}/{@code sum(pointsPossible)} across these rows,
 * computed server-side, never trusted from the client (spec §11).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "practical_attempt_questions")
public class PracticalAttemptQuestion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "practical_attempt_id", nullable = false)
    private PracticalAttempt practicalAttempt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "practical_question_id", nullable = false)
    private PracticalQuestion practicalQuestion;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "points_possible", nullable = false)
    private int pointsPossible;

    @Column(name = "points_earned")
    private Integer pointsEarned;

    @Column(name = "tests_passed")
    private Integer testsPassed;

    @Column(name = "tests_total")
    private Integer testsTotal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PracticalAttemptQuestionStatus status = PracticalAttemptQuestionStatus.NOT_ATTEMPTED;

    @Column(name = "submission_content", columnDefinition = "TEXT")
    private String submissionContent;

    @Enumerated(EnumType.STRING)
    @Column(name = "selected_language")
    private CodingLanguage selectedLanguage;

    @Column(name = "submission_file_url")
    private String submissionFileUrl;

    @Column(name = "submission_link_url")
    private String submissionLinkUrl;

    @Column(name = "first_successful_compilation_at")
    private Instant firstSuccessfulCompilationAt;

    @Column(columnDefinition = "TEXT")
    private String feedback;

    public PracticalAttemptQuestion(PracticalAttempt practicalAttempt, PracticalQuestion practicalQuestion,
            int displayOrder, int pointsPossible) {
        this.practicalAttempt = practicalAttempt;
        this.practicalQuestion = practicalQuestion;
        this.displayOrder = displayOrder;
        this.pointsPossible = pointsPossible;
    }
}

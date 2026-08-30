package com.studen.questionbank;

import com.studen.common.entity.BaseEntity;
import com.studen.skill.Skill;
import com.studen.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single question in the centralized Question Bank (Phase 7.1). Organized by the existing
 * {@link Skill} entity (reused, not duplicated) plus an optional {@link Topic}. Only
 * {@code status == PUBLISHED} questions are ever eligible for the future Assessment Engine
 * (Phase 7.2) or the learner-facing API — see {@code LearnerQuestionService}.
 *
 * <p>Versioning: editing a PUBLISHED question in place is forbidden (see
 * {@code QuestionBankService}) to preserve historical assessment integrity. Instead,
 * {@code previousVersion} links a new DRAFT row back to the PUBLISHED question it supersedes;
 * publishing the new version auto-archives the old one.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "questions")
public class Question extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id")
    private Topic topic;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    // Trim/lowercase/collapse-whitespace of questionText, recomputed on every save — used only for
    // the duplicate-question warning lookup (skillId + normalizedQuestionText), never shown to
    // any client.
    @Column(name = "normalized_question_text", nullable = false, columnDefinition = "TEXT")
    private String normalizedQuestionText;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false)
    private QuestionType questionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Difficulty difficulty;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuestionStatus status = QuestionStatus.DRAFT;

    @Column(name = "estimated_time_seconds")
    private Integer estimatedTimeSeconds;

    // Exactly one hierarchical tag string (e.g. "python-sets-operators"), never multiple tags —
    // see QuestionValidationService for the format rule.
    @Column(name = "tag", length = 150)
    private String tag;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(nullable = false)
    private int version = 1;

    // Set only when this row was created via "create new version" from a PUBLISHED question.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_version_id")
    private Question previousVersion;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    private List<QuestionOption> options = new ArrayList<>();

    public Question(Skill skill, String questionText, QuestionType questionType, Difficulty difficulty, User createdBy) {
        this.skill = skill;
        this.questionText = questionText;
        this.questionType = questionType;
        this.difficulty = difficulty;
        this.createdBy = createdBy;
    }
}

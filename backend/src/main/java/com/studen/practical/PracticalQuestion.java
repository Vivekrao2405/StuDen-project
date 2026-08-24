package com.studen.practical;

import com.studen.common.entity.BaseEntity;
import com.studen.questionbank.Difficulty;
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
 * One question inside a (possibly multi-question) {@link PracticalAssessment} — the actual problem
 * content (instructions/requirements/constraints/configurationJson), owning its own
 * {@link PracticalCodingLanguage}/{@link PracticalTestCase}/{@link PracticalRubricCriterion} rows.
 * Before this phase, all of this content lived directly on {@code PracticalAssessment} itself
 * (a 1:1 "one assessment = one problem" model) — see migration V25's javadoc-equivalent header
 * comment for the backward-compatible split.
 *
 * <p>{@code practicalType}/{@code workspaceType}/{@code evaluationType}/{@code timeLimitMinutes}
 * deliberately stay on the parent assessment (uniform across every question in it) — {@code skill}/
 * {@code difficulty} here are optional per-question overrides, falling back to the assessment's own
 * skill/difficulty when null (see {@code effectiveSkill}/{@code effectiveDifficulty} on the
 * response DTOs), which is what powers the per-skill performance breakdown (spec §15).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "practical_questions")
public class PracticalQuestion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "practical_assessment_id", nullable = false)
    private PracticalAssessment practicalAssessment;

    @Column(nullable = false)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "skill_id")
    private Skill skill;

    @Enumerated(EnumType.STRING)
    private Difficulty difficulty;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String instructions;

    @Column(columnDefinition = "TEXT")
    private String requirements;

    @Column(columnDefinition = "TEXT")
    private String constraints;

    @Column(name = "configuration_json", columnDefinition = "TEXT")
    private String configurationJson;

    @Column(nullable = false)
    private int points = 100;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    public PracticalQuestion(PracticalAssessment practicalAssessment, String title, String instructions, int points,
            int displayOrder) {
        this.practicalAssessment = practicalAssessment;
        this.title = title;
        this.instructions = instructions;
        this.points = points;
        this.displayOrder = displayOrder;
    }
}

package com.studen.practical;

import com.studen.common.entity.BaseEntity;
import com.studen.questionbank.Difficulty;
import com.studen.skill.Skill;
import com.studen.user.User;
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
 * A practical (non-MCQ) assessment definition — coding/web-dev/SQL/UI-UX/etc. Deliberately not
 * built on the {@code com.studen.assessment.Assessment}/{@code Question} model: a practical task
 * has no options to select, needs a code/design/file submission, and is usually manually
 * evaluated, none of which fits the MCQ shape (spec §1).
 *
 * <p>Versioning mirrors {@code com.studen.questionbank.Question} exactly: editing a PUBLISHED row
 * is forbidden (see {@code AdminPracticalAssessmentService}), "create new version" forks a new
 * DRAFT row via {@code previousVersion}, and publishing the new version auto-archives the old one.
 * A {@code PracticalAttempt} always points at the exact version it was started against, so
 * historical attempts are never affected by a later edit.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "practical_assessments")
public class PracticalAssessment extends BaseEntity {

    @Column(nullable = false)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    @Enumerated(EnumType.STRING)
    @Column(name = "practical_type", nullable = false)
    private PracticalType practicalType;

    @Enumerated(EnumType.STRING)
    @Column(name = "workspace_type", nullable = false)
    private WorkspaceType workspaceType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Difficulty difficulty;

    @Column(name = "time_limit_minutes", nullable = false)
    private int timeLimitMinutes;

    // Assessment-wide overview shown pre-start (e.g. "Complete all 5 questions before time runs
    // out") — never the problem statement itself, which lives per-question on PracticalQuestion.
    @Column(nullable = false, columnDefinition = "TEXT")
    private String instructions;

    @Enumerated(EnumType.STRING)
    @Column(name = "evaluation_type", nullable = false)
    private EvaluationType evaluationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PracticalAssessmentStatus status = PracticalAssessmentStatus.DRAFT;

    @Column(nullable = false)
    private int version = 1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_version_id")
    private PracticalAssessment previousVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    // Raw JSON text, parsed with Jackson in the service layer — per-workspaceType free-form
    // settings that don't warrant their own relational columns/tables (SQL schema description,
    // web starter template, UI/UX submission mode + reference image, generic dataset link). Kept
    // as plain TEXT rather than a Postgres jsonb column: no jsonb/Hibernate-JSON-type precedent
    // exists anywhere else in this schema, and the values here are read/written wholesale by the
    // owning workspace, never queried by field — a typed jsonb column would buy nothing.
    @Column(name = "configuration_json", columnDefinition = "TEXT")
    private String configurationJson;

    public PracticalAssessment(String title, Skill skill, PracticalType practicalType, WorkspaceType workspaceType,
            Difficulty difficulty, int timeLimitMinutes, String instructions, EvaluationType evaluationType,
            User createdBy) {
        this.title = title;
        this.skill = skill;
        this.practicalType = practicalType;
        this.workspaceType = workspaceType;
        this.difficulty = difficulty;
        this.timeLimitMinutes = timeLimitMinutes;
        this.instructions = instructions;
        this.evaluationType = evaluationType;
        this.createdBy = createdBy;
    }
}

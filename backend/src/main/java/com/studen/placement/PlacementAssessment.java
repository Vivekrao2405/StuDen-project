package com.studen.placement;

import com.studen.common.entity.BaseEntity;
import com.studen.questionbank.Difficulty;
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
 * An admin-configured, role-specific readiness assessment definition — the template, not an
 * attempt. Named {@code PlacementAssessment} because two other assessment concepts already exist
 * and neither fits:
 * <ul>
 *   <li>{@code com.studen.assessment.Assessment} is one row per student per auto-generated
 *       single-skill MCQ quiz, with no admin-authored template to configure at all.</li>
 *   <li>{@code com.studen.practical.PracticalAssessment} is a single practical task definition
 *       scoped to one skill, not a multi-skill role readiness test.</li>
 * </ul>
 * The template/attempt split used here deliberately mirrors PracticalAssessment/PracticalAttempt.
 *
 * <p>Phase 0 stores the configuration only. Question selection, timing enforcement, scoring and
 * skill-score derivation belong to later phases and must read this row rather than hard-code
 * anything about a role.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "placement_assessments")
public class PlacementAssessment extends BaseEntity {

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private PlacementRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Difficulty difficulty;

    // Null = untimed, the same convention as Assessment.timeLimitSeconds.
    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    // Percentage (0-100). Null = no pass/fail threshold configured.
    @Column(name = "passing_score")
    private Integer passingScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlacementContentStatus status = PlacementContentStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @OneToMany(mappedBy = "assessment", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    private List<PlacementAssessmentQuestion> questions = new ArrayList<>();

    public PlacementAssessment(String title, PlacementRole role, Difficulty difficulty, User createdBy) {
        this.title = title;
        this.role = role;
        this.difficulty = difficulty;
        this.createdBy = createdBy;
    }
}

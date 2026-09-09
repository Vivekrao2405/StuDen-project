package com.studen.placement;

import com.studen.common.entity.BaseEntity;
import com.studen.questionbank.Difficulty;
import com.studen.skill.Skill;
import com.studen.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An admin-authored placement preparation series (the Placement Prep track a student works
 * through). Carries every field the spec lists: name, description, company, target role, company
 * type, difficulty, duration, skills covered, thumbnail and status.
 *
 * <p>{@code company} and {@code companyType} are both nullable so a series can be role-only
 * ("SDE Prep"), company-specific, or scoped to a whole company type. {@code targetRole} is
 * required — a prep series always prepares for something.
 *
 * <p>Modules are owned by the series (cascade + orphanRemoval); skills covered are shared catalog
 * rows and are only linked.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "placement_series")
public class PlacementSeries extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private PlacementCompany company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_role_id", nullable = false)
    private PlacementRole targetRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "company_type")
    private CompanyType companyType;

    // Phase 5: the student-facing Preparation Type filter (spec §2/§3). Nullable — a series not
    // narrowed to one preparation focus simply leaves this unset.
    @Enumerated(EnumType.STRING)
    @Column(name = "preparation_type")
    private PreparationType preparationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Difficulty difficulty;

    // The spec calls this simply "Duration". Expressed in hours of study rather than calendar
    // days so it stays comparable with Resource.estimatedMinutes when a plan is assembled later.
    @Column(name = "estimated_duration_hours")
    private Integer estimatedDurationHours;

    @Column(name = "thumbnail_url", columnDefinition = "TEXT")
    private String thumbnailUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlacementContentStatus status = PlacementContentStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "placement_series_skills",
            joinColumns = @JoinColumn(name = "series_id"),
            inverseJoinColumns = @JoinColumn(name = "skill_id"))
    private Set<Skill> skillsCovered = new LinkedHashSet<>();

    @OneToMany(mappedBy = "series", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    private List<PlacementModule> modules = new ArrayList<>();

    public PlacementSeries(String name, PlacementRole targetRole, Difficulty difficulty, User createdBy) {
        this.name = name;
        this.targetRole = targetRole;
        this.difficulty = difficulty;
        this.createdBy = createdBy;
    }
}

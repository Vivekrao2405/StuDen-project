package com.studen.placement;

import com.studen.common.entity.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A placement target role (Software Developer / SDE, Data Analyst, ...). Admin-managed reference
 * data: the frontend must read these from the API, never hard-code them.
 *
 * <p>{@code normalizedName} is the lowercase/trimmed form used for duplicate detection, exactly
 * the convention {@code com.studen.skill.Skill} already uses.
 *
 * <p>{@code roleSkills} is the required-skills mapping and is owned by the role (cascade +
 * orphanRemoval), so replacing a role's skill set is a single collection assignment. The skills
 * themselves are shared catalog rows and are never touched by that cascade.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "placement_roles")
public class PlacementRole extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "normalized_name", nullable = false, unique = true)
    private String normalizedName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlacementCatalogStatus status = PlacementCatalogStatus.ACTIVE;

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @OneToMany(mappedBy = "role", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("priority ASC")
    private List<RoleSkill> roleSkills = new ArrayList<>();

    public PlacementRole(String name, String normalizedName, String description) {
        this.name = name;
        this.normalizedName = normalizedName;
        this.description = description;
    }
}

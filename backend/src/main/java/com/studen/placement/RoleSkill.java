package com.studen.placement;

import com.studen.assessment.AssessmentLevel;
import com.studen.common.entity.BaseEntity;
import com.studen.skill.Skill;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The Role -> Skill mapping, as a real relational row rather than a JSON blob, because the whole
 * placement loop hangs off being able to query it: "what skills does this role require?" feeds
 * assessment configuration, skill gaps and learning recommendations in later phases.
 *
 * <p>A role has many skills and a skill belongs to many roles; this entity carries the spec's
 * three optional mapping attributes:
 * <ul>
 *   <li>{@code weight} — how much this skill contributes to overall readiness for the role.</li>
 *   <li>{@code requiredProficiency} — the target level, reusing {@link AssessmentLevel}, the same
 *       5-tier scale a knowledge assessment already produces, so a later phase can compare
 *       measured against required without a second scale to reconcile. Null = no target set.</li>
 *   <li>{@code priority} — ordering for "which gap to close first"; 1 is the highest.</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "placement_role_skills",
        uniqueConstraints = @UniqueConstraint(name = "uq_placement_role_skill",
                columnNames = {"role_id", "skill_id"}))
public class RoleSkill extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private PlacementRole role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    @Column(nullable = false)
    private int weight = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "required_proficiency")
    private AssessmentLevel requiredProficiency;

    @Column(nullable = false)
    private int priority = 0;

    public RoleSkill(PlacementRole role, Skill skill, int weight, AssessmentLevel requiredProficiency, int priority) {
        this.role = role;
        this.skill = skill;
        this.weight = weight;
        this.requiredProficiency = requiredProficiency;
        this.priority = priority;
    }
}

package com.studen.placement;

import com.studen.common.entity.BaseEntity;
import com.studen.skill.Skill;
import com.studen.user.User;
import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A student placement profile — exactly one per {@link User}, enforced by a unique constraint so
 * a returning student can never be onboarded into a second profile.
 *
 * <p>Every multi-valued field is a real relation, never a comma-separated string:
 * {@code companyTypes} is an enum {@code @ElementCollection},
 * {@code targetCompanies}/{@code currentSkills} are join tables. Those join tables cascade only
 * from the profile side — deleting a profile removes its links, never the shared
 * {@link PlacementCompany} or {@link Skill} rows behind them.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "placement_profiles",
        uniqueConstraints = @UniqueConstraint(name = "uq_placement_profile_user", columnNames = "user_id"))
public class PlacementProfile extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_role_id", nullable = false)
    private PlacementRole targetRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "experience_level", nullable = false)
    private ExperienceLevel experienceLevel;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "placement_profile_company_types",
            joinColumns = @JoinColumn(name = "profile_id"))
    @Column(name = "company_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<CompanyType> companyTypes = new LinkedHashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "placement_profile_companies",
            joinColumns = @JoinColumn(name = "profile_id"),
            inverseJoinColumns = @JoinColumn(name = "company_id"))
    private Set<PlacementCompany> targetCompanies = new LinkedHashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "placement_profile_skills",
            joinColumns = @JoinColumn(name = "profile_id"),
            inverseJoinColumns = @JoinColumn(name = "skill_id"))
    private Set<Skill> currentSkills = new LinkedHashSet<>();

    // Free-text companies the student typed in themselves ("Can't find your company?"), never
    // promoted into the shared PlacementCompany catalog. Owned outright by the profile.
    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PlacementProfileManualCompany> manualTargetCompanies = new ArrayList<>();

    public PlacementProfile(User user, PlacementRole targetRole, ExperienceLevel experienceLevel) {
        this.user = user;
        this.targetRole = targetRole;
        this.experienceLevel = experienceLevel;
    }
}

package com.studen.skill;

import com.studen.common.entity.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.util.HashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "skills")
public class Skill extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "normalized_name", nullable = false, unique = true)
    private String normalizedName;

    @Column(nullable = false)
    private String category;

    /**
     * A simple-icons.org slug (e.g. "react", "postgresql"). Nullable — skills without a good
     * official icon fall back to a generic icon client-side rather than storing a broken/missing
     * image reference.
     */
    @Column(name = "icon_slug")
    private String iconSlug;

    // Lazy: SkillResponse never reads aliases, so leaving them eager would join skill_aliases
    // every time a skill loads via any other association (e.g. StudentPortfolio.skills), which
    // combined with that ManyToMany produces a nested multi-collection join. SkillSearchService,
    // the one place that ranks by alias, runs inside its own @Transactional method, so accessing
    // this collection there still works correctly.
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "skill_aliases", joinColumns = @JoinColumn(name = "skill_id"))
    @Column(name = "alias")
    private Set<String> aliases = new HashSet<>();

    public Skill(String name, String normalizedName, String category, String iconSlug) {
        this.name = name;
        this.normalizedName = normalizedName;
        this.category = category;
        this.iconSlug = iconSlug;
    }
}

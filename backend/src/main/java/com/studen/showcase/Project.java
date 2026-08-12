package com.studen.showcase;

import com.studen.common.entity.BaseEntity;
import com.studen.portfolio.StudentPortfolio;
import com.studen.skill.Skill;
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
import jakarta.persistence.OrderBy;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A piece of work a student showcases on their portfolio — not limited to software (design,
 * photography, video, music, research, sports, writing, business, etc.).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "projects")
public class Project extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private StudentPortfolio portfolio;

    @Column(nullable = false)
    private String title;

    @Column(name = "short_description")
    private String shortDescription;

    @Column(columnDefinition = "TEXT")
    private String description;

    // Defaults to PUBLIC: a student who bothers to create a showcase entry is presumably ready
    // to show it off (matches StudentPortfolio.available's default-true precedent); they can
    // flip to Private if not.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectVisibility visibility = ProjectVisibility.PUBLIC;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "project_skills", joinColumns = @JoinColumn(name = "project_id"),
            inverseJoinColumns = @JoinColumn(name = "skill_id"))
    private Set<Skill> skills = new LinkedHashSet<>();

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    private List<ProjectMedia> media = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "project_links", joinColumns = @JoinColumn(name = "project_id"))
    @OrderColumn(name = "link_order")
    private List<ProjectLink> links = new ArrayList<>();

    public Project(StudentPortfolio portfolio, String title) {
        this.portfolio = portfolio;
        this.title = title;
    }
}

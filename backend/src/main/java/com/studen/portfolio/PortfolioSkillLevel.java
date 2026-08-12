package com.studen.portfolio;

import com.studen.common.entity.BaseEntity;
import com.studen.skill.Skill;
import com.studen.skill.SkillLevel;
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
 * A student's self-reported proficiency for one skill on their portfolio. Deliberately a separate
 * table from the portfolio_skills join table (which only tracks which skills are attached) so
 * setting/changing a level never touches the skill-attachment flow at all.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "portfolio_skill_levels",
        uniqueConstraints = @UniqueConstraint(columnNames = {"portfolio_id", "skill_id"}))
public class PortfolioSkillLevel extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private StudentPortfolio portfolio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SkillLevel level;

    public PortfolioSkillLevel(StudentPortfolio portfolio, Skill skill, SkillLevel level) {
        this.portfolio = portfolio;
        this.skill = skill;
        this.level = level;
    }
}

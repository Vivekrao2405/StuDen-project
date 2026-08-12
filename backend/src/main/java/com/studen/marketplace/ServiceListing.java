package com.studen.marketplace;

import com.studen.common.entity.BaseEntity;
import com.studen.portfolio.StudentPortfolio;
import com.studen.skill.Skill;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A service a student offers on the marketplace (e.g. "Power BI Dashboard Development"). Named
 * "ServiceListing" rather than "Service" to avoid colliding with
 * {@code org.springframework.stereotype.Service}, used throughout this codebase. Discovery/
 * foundation only — no pricing, booking, or order-management fields; those belong to a later
 * phase once service creation itself is built.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "services")
public class ServiceListing extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private StudentPortfolio portfolio;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MarketplaceCategory category;

    private String location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ServiceStatus status = ServiceStatus.ACTIVE;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "service_skills", joinColumns = @JoinColumn(name = "service_id"),
            inverseJoinColumns = @JoinColumn(name = "skill_id"))
    private Set<Skill> skills = new LinkedHashSet<>();

    public ServiceListing(StudentPortfolio portfolio, String title, MarketplaceCategory category) {
        this.portfolio = portfolio;
        this.title = title;
        this.category = category;
    }
}

package com.studen.marketplace;

import com.studen.common.entity.BaseEntity;
import com.studen.portfolio.StudentPortfolio;
import com.studen.showcase.Project;
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
 * A service a student offers on the marketplace (e.g. "Power BI Dashboard Development"). Named
 * "ServiceListing" rather than "Service" to avoid colliding with
 * {@code org.springframework.stereotype.Service}, used throughout this codebase. Phase 6.1 was
 * discovery-only; Phase 6.3 added pricing/delivery/availability/media/links/deliverables and
 * reference-only links to the student's own showcase projects.
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

    // Every new service starts as DRAFT (see ServiceListingService.createService) — the only way
    // to reach ACTIVE is the dedicated /publish endpoint, which validates required fields first.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ServiceStatus status = ServiceStatus.DRAFT;

    // Independent of `status`: an ACTIVE-but-unavailable service stays published/discoverable,
    // just clearly labeled "Currently unavailable" — never deleted or hidden for this reason.
    @Column(nullable = false)
    private boolean available = true;

    // Major-unit amount (e.g. 1500 = "₹1,500") — nullable while DRAFT, required to publish.
    @Column(name = "price_amount")
    private Integer priceAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ServiceCurrency currency = ServiceCurrency.INR;

    // Nullable while DRAFT, required to publish. "Custom" in the UI is just any positive integer
    // typed directly — no separate flag needed.
    @Column(name = "delivery_days")
    private Integer deliveryDays;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "service_skills", joinColumns = @JoinColumn(name = "service_id"),
            inverseJoinColumns = @JoinColumn(name = "skill_id"))
    private Set<Skill> skills = new LinkedHashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "service_deliverables", joinColumns = @JoinColumn(name = "service_id"))
    @OrderColumn(name = "item_order")
    @Column(name = "description", nullable = false)
    private List<String> whatYoullReceive = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "service_links", joinColumns = @JoinColumn(name = "service_id"))
    @OrderColumn(name = "link_order")
    private List<ServiceLink> links = new ArrayList<>();

    @OneToMany(mappedBy = "service", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("displayOrder ASC")
    private List<ServiceMedia> media = new ArrayList<>();

    // Reference-only — never duplicates Project's fields. No @OrderColumn: selection order isn't
    // a product requirement here, unlike media/links ordering.
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "service_projects", joinColumns = @JoinColumn(name = "service_id"),
            inverseJoinColumns = @JoinColumn(name = "project_id"))
    private Set<Project> linkedProjects = new LinkedHashSet<>();

    public ServiceListing(StudentPortfolio portfolio, String title, MarketplaceCategory category) {
        this.portfolio = portfolio;
        this.title = title;
        this.category = category;
    }
}

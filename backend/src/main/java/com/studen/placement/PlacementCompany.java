package com.studen.placement;

import com.studen.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A company students can target. Named {@code PlacementCompany} (table
 * {@code placement_companies}) rather than plain {@code Company} so the placement namespace stays
 * self-contained and a future non-placement company concept cannot collide with it.
 *
 * <p>No company-specific preparation content is seeded anywhere: the spec requires that company
 * preparation use StuDen's configured/verified content and never invent claims about what a
 * company asks, so every company row is admin-authored.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "placement_companies")
public class PlacementCompany extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "normalized_name", nullable = false, unique = true)
    private String normalizedName;

    @Enumerated(EnumType.STRING)
    @Column(name = "company_type", nullable = false)
    private CompanyType companyType;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "logo_url", columnDefinition = "TEXT")
    private String logoUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PlacementCatalogStatus status = PlacementCatalogStatus.ACTIVE;

    public PlacementCompany(String name, String normalizedName, CompanyType companyType) {
        this.name = name;
        this.normalizedName = normalizedName;
        this.companyType = companyType;
    }
}

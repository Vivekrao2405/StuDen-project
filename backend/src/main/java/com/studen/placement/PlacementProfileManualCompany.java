package com.studen.placement;

import com.studen.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A student-entered target company that is not in the admin-managed {@link PlacementCompany}
 * catalog ("Can't find your company?"). Owned entirely by the {@link PlacementProfile} that typed
 * it — a name here is never promoted into the shared catalog and is never claimed to be
 * StuDen-verified, unlike an official {@link PlacementCompany}.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "placement_profile_manual_companies",
        uniqueConstraints = @UniqueConstraint(name = "uq_placement_profile_manual_company",
                columnNames = {"profile_id", "name"}))
public class PlacementProfileManualCompany extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    private PlacementProfile profile;

    @Column(nullable = false)
    private String name;

    public PlacementProfileManualCompany(PlacementProfile profile, String name) {
        this.profile = profile;
        this.name = name;
    }
}

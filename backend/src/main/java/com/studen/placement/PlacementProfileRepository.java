package com.studen.placement;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlacementProfileRepository extends JpaRepository<PlacementProfile, UUID> {

    Optional<PlacementProfile> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    // Guards role deletion: a role a student has already targeted must not disappear from under
    // that student.
    boolean existsByTargetRoleId(UUID roleId);

    // Guards company deletion. targetCompanies is a join table, so this counts link rows.
    @Query("select count(p) > 0 from PlacementProfile p join p.targetCompanies c where c.id = :companyId")
    boolean existsByTargetCompanyId(@Param("companyId") UUID companyId);
}

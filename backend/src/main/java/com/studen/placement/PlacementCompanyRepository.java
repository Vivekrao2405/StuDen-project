package com.studen.placement;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlacementCompanyRepository extends JpaRepository<PlacementCompany, UUID> {

    Optional<PlacementCompany> findByNormalizedName(String normalizedName);

    boolean existsByNormalizedName(String normalizedName);

    // Same null-coalescing / empty-string-sentinel pattern as ResourceRepository.search — binding
    // a null directly breaks Postgres type resolution inside lower(concat(...)).
    @Query("""
            select c from PlacementCompany c
            where (:companyType is null or c.companyType = :companyType)
              and (:status is null or c.status = :status)
              and (:search = '' or lower(c.name) like lower(concat('%', :search, '%')))
            """)
    Page<PlacementCompany> search(@Param("companyType") CompanyType companyType,
            @Param("status") PlacementCatalogStatus status, @Param("search") String search, Pageable pageable);
}

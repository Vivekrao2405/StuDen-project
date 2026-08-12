package com.studen.marketplace;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ServiceListingRepository extends JpaRepository<ServiceListing, UUID> {

    // Only ACTIVE listings are ever eligible for marketplace discovery — DRAFT/INACTIVE never
    // appear here regardless of the caller's other filters. `cap` bounds how many candidate rows
    // this pulls into memory before MarketplaceSearchService merges/sorts/paginates across both
    // sources (see that class for why: no search index yet, and this is the app's first
    // paginated query, so Pageable is used purely as a LIMIT here, not a real Page<> result).
    //
    // q/location/skillName use an empty-string sentinel for "not provided", not null — see
    // StudentPortfolioRepository.searchCandidates for why a genuinely null parameter bound into
    // lower(concat(...)) breaks Postgres's type resolution. `category` has no lower()/concat()
    // involved, so a null enum parameter is fine as-is.
    @Query("""
            select distinct sl from ServiceListing sl
            left join sl.skills sk
            where sl.status = 'ACTIVE'
              and (:q = '' or
                   lower(sl.title) like lower(concat('%', :q, '%')) or
                   lower(sl.description) like lower(concat('%', :q, '%')) or
                   lower(sk.name) like lower(concat('%', :q, '%')))
              and (:category is null or sl.category = :category)
              and (:location = '' or lower(sl.location) like lower(concat('%', :location, '%')))
              and (:skillName = '' or lower(sk.name) = lower(:skillName))
            order by sl.createdAt desc
            """)
    List<ServiceListing> searchCandidates(@Param("q") String q, @Param("category") MarketplaceCategory category,
            @Param("location") String location, @Param("skillName") String skillName, Pageable cap);
}

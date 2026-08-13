package com.studen.portfolio;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentPortfolioRepository extends JpaRepository<StudentPortfolio, UUID> {

    Optional<StudentPortfolio> findByUserId(UUID userId);

    // Batch lookup for com.studen.booking.ServiceRequestService — resolving requester/provider
    // display info (headline/slug/photo) for a list of requests in one query instead of one
    // findByUserId per row.
    List<StudentPortfolio> findAllByUserIdIn(List<UUID> userIds);

    boolean existsByUserId(UUID userId);

    boolean existsByPublicSlug(String publicSlug);

    Optional<StudentPortfolio> findByPublicSlug(String publicSlug);

    // Used by marketplace search (com.studen.marketplace.MarketplaceSearchService). `cap` bounds
    // how many candidate rows are pulled into memory before that service merges/sorts/paginates
    // across both marketplace sources — see that class for why. Category filtering isn't here
    // because a student's marketplace category is derived from their skills' categories, not a
    // column on this table — that happens in Java after this query returns.
    //
    // q/location/skillName use an empty-string sentinel for "not provided", not null: binding a
    // genuinely null parameter into lower(concat('%', :param, '%')) makes Postgres unable to
    // resolve the parameter's type (it falls back to bytea, which lower() has no overload for,
    // and the query fails with "function lower(bytea) does not exist") — even though the
    // ":param = '' or ..." branch would short-circuit it logically, SQL still type-checks the
    // whole expression. The caller (MarketplaceSearchService) never passes null here.
    @Query("""
            select distinct p from StudentPortfolio p
            join p.user u
            left join p.skills s
            where (:q = '' or
                   lower(u.fullName) like lower(concat('%', :q, '%')) or
                   lower(p.headline) like lower(concat('%', :q, '%')) or
                   lower(s.name) like lower(concat('%', :q, '%')))
              and (:location = '' or lower(p.location) like lower(concat('%', :location, '%')))
              and (:availableOnly = false or p.available = true)
              and (:skillName = '' or lower(s.name) = lower(:skillName))
            order by p.createdAt desc
            """)
    List<StudentPortfolio> searchCandidates(@Param("q") String q, @Param("location") String location,
            @Param("availableOnly") boolean availableOnly, @Param("skillName") String skillName, Pageable cap);
}

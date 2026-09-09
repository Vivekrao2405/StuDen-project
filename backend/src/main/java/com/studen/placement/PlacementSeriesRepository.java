package com.studen.placement;

import com.studen.questionbank.Difficulty;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlacementSeriesRepository extends JpaRepository<PlacementSeries, UUID> {

    boolean existsByTargetRoleId(UUID roleId);

    boolean existsByCompanyId(UUID companyId);

    @Query("""
            select s from PlacementSeries s
            where (:roleId is null or s.targetRole.id = :roleId)
              and (:companyId is null or s.company.id = :companyId)
              and (:companyType is null or s.companyType = :companyType)
              and (:preparationType is null or s.preparationType = :preparationType)
              and (:status is null or s.status = :status)
              and (:search = '' or lower(s.name) like lower(concat('%', :search, '%')))
            """)
    Page<PlacementSeries> search(@Param("roleId") UUID roleId, @Param("companyId") UUID companyId,
            @Param("companyType") CompanyType companyType, @Param("preparationType") PreparationType preparationType,
            @Param("status") PlacementContentStatus status, @Param("search") String search, Pageable pageable);

    // Student catalog: PUBLISHED only, optionally narrowed by skill (any skillsCovered match).
    @Query("""
            select distinct s from PlacementSeries s
            left join s.skillsCovered sk
            where s.status = com.studen.placement.PlacementContentStatus.PUBLISHED
              and (:roleId is null or s.targetRole.id = :roleId)
              and (:companyId is null or s.company.id = :companyId)
              and (:companyType is null or s.companyType = :companyType)
              and (:preparationType is null or s.preparationType = :preparationType)
              and (:difficulty is null or s.difficulty = :difficulty)
              and (:skillId is null or sk.id = :skillId)
              and (:search = '' or lower(s.name) like lower(concat('%', :search, '%')))
            """)
    Page<PlacementSeries> searchPublished(@Param("roleId") UUID roleId, @Param("companyId") UUID companyId,
            @Param("companyType") CompanyType companyType, @Param("preparationType") PreparationType preparationType,
            @Param("difficulty") Difficulty difficulty, @Param("skillId") UUID skillId,
            @Param("search") String search, Pageable pageable);

    @Query("""
            select new com.studen.placement.IdCountView(m.series.id, count(m))
            from PlacementModule m where m.series.id in :seriesIds
            group by m.series.id
            """)
    List<IdCountView> countModulesBySeries(@Param("seriesIds") List<UUID> seriesIds);
}

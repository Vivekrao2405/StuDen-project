package com.studen.placement;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlacementRoleRepository extends JpaRepository<PlacementRole, UUID> {

    Optional<PlacementRole> findByNormalizedName(String normalizedName);

    boolean existsByNormalizedName(String normalizedName);

    List<PlacementRole> findAllByOrderByDisplayOrderAscNameAsc();

    List<PlacementRole> findAllByStatusOrderByDisplayOrderAscNameAsc(PlacementCatalogStatus status);

    // Role -> Required Skills, the query the whole placement loop hangs off. Fetch-joins the skill
    // so a response DTO never triggers one lazy load per mapping row.
    @Query("""
            select rs from RoleSkill rs
            join fetch rs.skill
            where rs.role.id = :roleId
            order by rs.priority asc, rs.skill.name asc
            """)
    List<RoleSkill> findRoleSkills(@Param("roleId") UUID roleId);

    // One grouped query for a whole page of roles, instead of touching each role lazy roleSkills
    // collection to size it.
    @Query("""
            select new com.studen.placement.IdCountView(rs.role.id, count(rs))
            from RoleSkill rs group by rs.role.id
            """)
    List<IdCountView> countSkillsByRole();
}

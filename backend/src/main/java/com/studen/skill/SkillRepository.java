package com.studen.skill;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SkillRepository extends JpaRepository<Skill, UUID> {

    List<Skill> findAllByIdIn(Set<UUID> ids);

    Optional<Skill> findByNormalizedName(String normalizedName);

    // Admin list — same null-coalescing/empty-string-sentinel pattern as
    // PlacementCompanyRepository.search (a bound null breaks Postgres's type resolution inside
    // lower(concat(...))).
    @Query("""
            select s from Skill s
            where (:category is null or s.category = :category)
              and (:search = '' or lower(s.name) like lower(concat('%', :search, '%'))
                   or lower(s.category) like lower(concat('%', :search, '%')))
            """)
    Page<Skill> searchAdmin(@Param("search") String search, @Param("category") String category, Pageable pageable);

    // Whether any other table currently points at this skill. Checked up front before an admin
    // delete so a shared catalog skill is never removed out from under questions, assessments,
    // resources, role mappings, or a student's own portfolio/marketplace/showcase data — several
    // of those FKs cascade on skill delete, so relying on a DB constraint violation alone would
    // silently wipe that data instead of blocking the delete.
    @Query(value = """
            select exists (
                select 1 from topics where skill_id = :skillId
                union all select 1 from questions where skill_id = :skillId
                union all select 1 from assessments where skill_id = :skillId
                union all select 1 from practical_assessments where skill_id = :skillId
                union all select 1 from practical_questions where skill_id = :skillId
                union all select 1 from resources where skill_id = :skillId
                union all select 1 from placement_role_skills where skill_id = :skillId
                union all select 1 from placement_profile_skills where skill_id = :skillId
                union all select 1 from placement_skill_scores where skill_id = :skillId
                union all select 1 from placement_series_skills where skill_id = :skillId
                union all select 1 from portfolio_skills where skill_id = :skillId
                union all select 1 from portfolio_skill_levels where skill_id = :skillId
                union all select 1 from service_skills where skill_id = :skillId
                union all select 1 from project_skills where skill_id = :skillId
            )
            """, nativeQuery = true)
    boolean isInUse(@Param("skillId") UUID skillId);

    @Query("""
            select distinct s from Skill s left join s.aliases a
            where lower(s.normalizedName) like lower(concat('%', :term, '%'))
               or lower(a) like lower(concat('%', :term, '%'))
               or lower(s.category) like lower(concat('%', :term, '%'))
            """)
    List<Skill> search(@Param("term") String term);

    @Query("select distinct s.category from Skill s order by s.category")
    List<String> findDistinctCategories();
}

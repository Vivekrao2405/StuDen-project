package com.studen.skill;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SkillRepository extends JpaRepository<Skill, UUID> {

    List<Skill> findAllByIdIn(Set<UUID> ids);

    Optional<Skill> findByNormalizedName(String normalizedName);

    @Query("""
            select distinct s from Skill s left join s.aliases a
            where lower(s.normalizedName) like lower(concat('%', :term, '%'))
               or lower(a) like lower(concat('%', :term, '%'))
            """)
    List<Skill> search(@Param("term") String term);
}

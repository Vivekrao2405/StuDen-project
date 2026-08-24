package com.studen.resource;

import com.studen.questionbank.Difficulty;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResourceRepository extends JpaRepository<Resource, UUID> {

    // Admin list — same null-coalescing/empty-string-sentinel pattern as
    // PracticalAssessmentRepository.search (a bound null breaks Postgres's type resolution inside
    // lower(concat(...))).
    @Query("""
            select r from Resource r
            where (:skillId is null or r.skill.id = :skillId)
              and (:resourceType is null or r.resourceType = :resourceType)
              and (:difficulty is null or r.difficulty = :difficulty)
              and (:status is null or r.status = :status)
              and (:search = '' or lower(r.title) like lower(concat('%', :search, '%')))
            """)
    Page<Resource> search(@Param("skillId") UUID skillId, @Param("resourceType") ResourceType resourceType,
            @Param("difficulty") Difficulty difficulty, @Param("status") ResourceStatus status,
            @Param("search") String search, Pageable pageable);

    // Student-facing single lookup — PUBLISHED only, mirrors
    // PracticalAssessmentRepository.findByIdAndStatus (spec §23: drafts/archived must never be
    // visible to students, even by direct id).
    Optional<Resource> findByIdAndStatus(UUID id, ResourceStatus status);

    // Backs ResourceMatchingService — every published resource for a set of weak-area skills.
    // Bounded by the student's own portfolio skill count, so this is never an unbounded scan.
    @Query("select r from Resource r where r.status = com.studen.resource.ResourceStatus.PUBLISHED and r.skill.id in :skillIds")
    List<Resource> findPublishedForSkills(@Param("skillIds") Set<UUID> skillIds);
}

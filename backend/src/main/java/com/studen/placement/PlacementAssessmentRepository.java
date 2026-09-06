package com.studen.placement;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlacementAssessmentRepository extends JpaRepository<PlacementAssessment, UUID> {

    boolean existsByRoleId(UUID roleId);

    @Query("""
            select a from PlacementAssessment a
            where (:roleId is null or a.role.id = :roleId)
              and (:status is null or a.status = :status)
              and (:search = '' or lower(a.title) like lower(concat('%', :search, '%')))
            """)
    Page<PlacementAssessment> search(@Param("roleId") UUID roleId,
            @Param("status") PlacementContentStatus status, @Param("search") String search, Pageable pageable);

    // Detail view: pulls the whole assessment plus its configured questions in one query rather
    // than one lazy load per link row.
    @Query("""
            select distinct a from PlacementAssessment a
            left join fetch a.questions q
            left join fetch q.question
            where a.id = :id
            """)
    Optional<PlacementAssessment> findByIdWithQuestions(@Param("id") UUID id);

    @Query("""
            select new com.studen.placement.IdCountView(q.assessment.id, count(q))
            from PlacementAssessmentQuestion q where q.assessment.id in :assessmentIds
            group by q.assessment.id
            """)
    List<IdCountView> countQuestionsByAssessment(@Param("assessmentIds") List<UUID> assessmentIds);
}

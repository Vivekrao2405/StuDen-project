package com.studen.placement;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlacementModuleItemRepository extends JpaRepository<PlacementModuleItem, UUID> {

    List<PlacementModuleItem> findByModuleIdOrderByDisplayOrderAsc(UUID moduleId);

    // Phase 6 AI Coach: best-effort reverse lookup from a PracticalAssessment back to the placement
    // module item (if any) that references it, so AI Coach can enrich its prompt with role/series
    // context. Not guaranteed unique across modules (existsByModuleIdAndPracticalAssessmentId is the
    // module-scoped uniqueness guard used by the admin add-item flow) -- first match is enough here,
    // this is only ever used for display context, never authorization.
    Optional<PlacementModuleItem> findFirstByPracticalAssessmentId(UUID practicalAssessmentId);

    // Batches the Phase 5 progress roll-up across every module on a page of series in one query.
    List<PlacementModuleItem> findByModuleIdInOrderByDisplayOrderAsc(Collection<UUID> moduleIds);

    boolean existsByModuleIdAndQuestionId(UUID moduleId, UUID questionId);

    boolean existsByModuleIdAndPracticalAssessmentId(UUID moduleId, UUID practicalAssessmentId);

    boolean existsByModuleIdAndResourceId(UUID moduleId, UUID resourceId);
}

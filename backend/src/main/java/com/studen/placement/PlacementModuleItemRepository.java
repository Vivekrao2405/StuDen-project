package com.studen.placement;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlacementModuleItemRepository extends JpaRepository<PlacementModuleItem, UUID> {

    List<PlacementModuleItem> findByModuleIdOrderByDisplayOrderAsc(UUID moduleId);

    // Batches the Phase 5 progress roll-up across every module on a page of series in one query.
    List<PlacementModuleItem> findByModuleIdInOrderByDisplayOrderAsc(Collection<UUID> moduleIds);

    boolean existsByModuleIdAndQuestionId(UUID moduleId, UUID questionId);

    boolean existsByModuleIdAndPracticalAssessmentId(UUID moduleId, UUID practicalAssessmentId);

    boolean existsByModuleIdAndResourceId(UUID moduleId, UUID resourceId);
}

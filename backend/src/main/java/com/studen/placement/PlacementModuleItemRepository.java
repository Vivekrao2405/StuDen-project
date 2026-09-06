package com.studen.placement;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlacementModuleItemRepository extends JpaRepository<PlacementModuleItem, UUID> {

    List<PlacementModuleItem> findByModuleIdOrderByDisplayOrderAsc(UUID moduleId);

    boolean existsByModuleIdAndQuestionId(UUID moduleId, UUID questionId);

    boolean existsByModuleIdAndPracticalAssessmentId(UUID moduleId, UUID practicalAssessmentId);

    boolean existsByModuleIdAndResourceId(UUID moduleId, UUID resourceId);
}

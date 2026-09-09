package com.studen.placement;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StudentModuleItemProgressRepository extends JpaRepository<StudentModuleItemProgress, UUID> {

    Optional<StudentModuleItemProgress> findByStudentIdAndModuleItemId(UUID studentId, UUID moduleItemId);

    List<StudentModuleItemProgress> findByStudentId(UUID studentId);

    // Batches Phase 5's QUESTION-item progress lookup across every item on a page of series.
    List<StudentModuleItemProgress> findAllByStudentIdAndModuleItemIdIn(UUID studentId, Collection<UUID> moduleItemIds);

    boolean existsByModuleItemId(UUID moduleItemId);

    // Guards module deletion: any student progress on any item in the module blocks the delete.
    @Query("select count(p) > 0 from StudentModuleItemProgress p where p.moduleItem.module.id = :moduleId")
    boolean existsByModuleId(@Param("moduleId") UUID moduleId);

    // Guards series deletion at item granularity (the series roll-up row may not exist yet if a
    // student jumped straight into an item).
    @Query("select count(p) > 0 from StudentModuleItemProgress p where p.moduleItem.module.series.id = :seriesId")
    boolean existsBySeriesId(@Param("seriesId") UUID seriesId);
}

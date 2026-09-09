package com.studen.placement;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlacementModuleRepository extends JpaRepository<PlacementModule, UUID> {

    List<PlacementModule> findBySeriesIdOrderByDisplayOrderAsc(UUID seriesId);

    // Batches the Phase 5 student catalog's progress roll-up across an entire page of series in
    // one query, instead of one findBySeriesId... call per series.
    List<PlacementModule> findBySeriesIdInOrderByDisplayOrderAsc(Collection<UUID> seriesIds);
}

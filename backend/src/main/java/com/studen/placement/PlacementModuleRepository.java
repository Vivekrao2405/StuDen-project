package com.studen.placement;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlacementModuleRepository extends JpaRepository<PlacementModule, UUID> {

    List<PlacementModule> findBySeriesIdOrderByDisplayOrderAsc(UUID seriesId);
}

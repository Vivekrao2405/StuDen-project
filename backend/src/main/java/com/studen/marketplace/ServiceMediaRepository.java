package com.studen.marketplace;

import com.studen.showcase.ProjectMediaType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceMediaRepository extends JpaRepository<ServiceMedia, UUID> {

    List<ServiceMedia> findAllByServiceIdOrderByDisplayOrderAsc(UUID serviceId);

    // Batch-fetches media for many services in one query (grouped by service id by the caller)
    // instead of one query per service — this is the N+1 lesson from the recent showcase perf
    // pass, applied here from the start rather than shipped as a later fix.
    List<ServiceMedia> findAllByServiceIdInOrderByDisplayOrderAsc(List<UUID> serviceIds);

    Optional<ServiceMedia> findByIdAndServiceId(UUID id, UUID serviceId);

    long countByServiceIdAndMediaType(UUID serviceId, ProjectMediaType mediaType);
}

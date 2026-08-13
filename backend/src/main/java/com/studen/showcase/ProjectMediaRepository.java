package com.studen.showcase;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectMediaRepository extends JpaRepository<ProjectMedia, UUID> {

    List<ProjectMedia> findAllByProjectIdOrderByDisplayOrderAsc(UUID projectId);

    // Used to batch-load media for a whole page of projects (dashboard/showcase list, public
    // profile) in one round trip instead of one findAllByProjectId query per project (N+1).
    List<ProjectMedia> findAllByProjectIdInOrderByDisplayOrderAsc(List<UUID> projectIds);

    Optional<ProjectMedia> findByIdAndProjectId(UUID id, UUID projectId);

    long countByProjectIdAndMediaType(UUID projectId, ProjectMediaType mediaType);
}

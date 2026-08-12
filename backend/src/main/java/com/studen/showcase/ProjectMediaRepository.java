package com.studen.showcase;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectMediaRepository extends JpaRepository<ProjectMedia, UUID> {

    List<ProjectMedia> findAllByProjectIdOrderByDisplayOrderAsc(UUID projectId);

    Optional<ProjectMedia> findByIdAndProjectId(UUID id, UUID projectId);

    long countByProjectIdAndMediaType(UUID projectId, ProjectMediaType mediaType);
}

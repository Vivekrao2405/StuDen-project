package com.studen.showcase;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    List<Project> findAllByPortfolioIdOrderByCreatedAtDesc(UUID portfolioId);

    Optional<Project> findByIdAndPortfolioId(UUID id, UUID portfolioId);

    List<Project> findAllByPortfolioIdAndVisibilityOrderByCreatedAtDesc(UUID portfolioId, ProjectVisibility visibility);

    long countByPortfolioId(UUID portfolioId);
}

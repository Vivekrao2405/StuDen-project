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

    // Used by the marketplace module to verify, in one query, that every project a student wants
    // to link to a service both belongs to them (portfolioId) and is publicly visible
    // (visibility) — the core IDOR/privacy guard for showcase-project linking. If the returned
    // list's size doesn't match the requested id count, at least one id failed one of those
    // checks (someone else's project, your own private project, or a nonexistent id).
    List<Project> findAllByIdInAndPortfolioIdAndVisibility(List<UUID> ids, UUID portfolioId, ProjectVisibility visibility);
}

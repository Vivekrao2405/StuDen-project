package com.studen.portfolio;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioSkillLevelRepository extends JpaRepository<PortfolioSkillLevel, UUID> {

    List<PortfolioSkillLevel> findAllByPortfolioId(UUID portfolioId);

    Optional<PortfolioSkillLevel> findByPortfolioIdAndSkillId(UUID portfolioId, UUID skillId);
}

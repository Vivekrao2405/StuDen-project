package com.studen.education;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EducationRepository extends JpaRepository<Education, UUID> {

    List<Education> findAllByPortfolioIdOrderByStartYearDesc(UUID portfolioId);

    Optional<Education> findByIdAndPortfolioId(UUID id, UUID portfolioId);
}

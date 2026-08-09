package com.studen.portfolio;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StudentPortfolioRepository extends JpaRepository<StudentPortfolio, UUID> {

    Optional<StudentPortfolio> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    boolean existsByPublicSlug(String publicSlug);

    Optional<StudentPortfolio> findByPublicSlug(String publicSlug);
}

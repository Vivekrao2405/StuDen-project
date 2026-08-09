package com.studen.share;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileShareRepository extends JpaRepository<ProfileShare, UUID> {

    Optional<ProfileShare> findByPortfolioId(UUID portfolioId);
}

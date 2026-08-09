package com.studen.certificate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CertificateRepository extends JpaRepository<Certificate, UUID> {

    List<Certificate> findAllByPortfolioIdOrderByIssueDateDesc(UUID portfolioId);

    Optional<Certificate> findByIdAndPortfolioId(UUID id, UUID portfolioId);
}

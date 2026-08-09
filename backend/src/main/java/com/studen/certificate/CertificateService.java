package com.studen.certificate;

import com.studen.common.exception.ResourceNotFoundException;
import com.studen.portfolio.StudentPortfolio;
import com.studen.portfolio.StudentPortfolioRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CertificateService {

    private final CertificateRepository certificateRepository;
    private final StudentPortfolioRepository portfolioRepository;

    public CertificateService(CertificateRepository certificateRepository, StudentPortfolioRepository portfolioRepository) {
        this.certificateRepository = certificateRepository;
        this.portfolioRepository = portfolioRepository;
    }

    public List<CertificateResponse> getMyCertificates(UUID userId) {
        StudentPortfolio portfolio = findOwnPortfolio(userId);
        return certificateRepository.findAllByPortfolioIdOrderByIssueDateDesc(portfolio.getId()).stream()
                .map(CertificateResponse::from)
                .toList();
    }

    @Transactional
    public CertificateResponse createCertificate(UUID userId, CertificateRequest request) {
        StudentPortfolio portfolio = findOwnPortfolio(userId);

        Certificate certificate = new Certificate(portfolio, request.title());
        certificate.setIssuedBy(request.issuedBy());
        certificate.setIssueDate(request.issueDate());
        certificate.setCertificateUrl(request.certificateUrl());

        return CertificateResponse.from(certificateRepository.save(certificate));
    }

    @Transactional
    public CertificateResponse updateCertificate(UUID userId, UUID certificateId, CertificateRequest request) {
        StudentPortfolio portfolio = findOwnPortfolio(userId);
        Certificate certificate = findOwnCertificate(portfolio.getId(), certificateId);

        certificate.setTitle(request.title());
        certificate.setIssuedBy(request.issuedBy());
        certificate.setIssueDate(request.issueDate());
        certificate.setCertificateUrl(request.certificateUrl());

        return CertificateResponse.from(certificate);
    }

    @Transactional
    public void deleteCertificate(UUID userId, UUID certificateId) {
        StudentPortfolio portfolio = findOwnPortfolio(userId);
        Certificate certificate = findOwnCertificate(portfolio.getId(), certificateId);
        certificateRepository.delete(certificate);
    }

    private StudentPortfolio findOwnPortfolio(UUID userId) {
        return portfolioRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Student portfolio not found"));
    }

    private Certificate findOwnCertificate(UUID portfolioId, UUID certificateId) {
        return certificateRepository.findByIdAndPortfolioId(certificateId, portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Certificate not found"));
    }
}

package com.studen.education;

import com.studen.common.exception.InvalidRequestException;
import com.studen.common.exception.ResourceNotFoundException;
import com.studen.portfolio.StudentPortfolio;
import com.studen.portfolio.StudentPortfolioRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EducationService {

    private final EducationRepository educationRepository;
    private final StudentPortfolioRepository portfolioRepository;

    public EducationService(EducationRepository educationRepository, StudentPortfolioRepository portfolioRepository) {
        this.educationRepository = educationRepository;
        this.portfolioRepository = portfolioRepository;
    }

    public List<EducationResponse> getMyEducation(UUID userId) {
        StudentPortfolio portfolio = findOwnPortfolio(userId);
        return educationRepository.findAllByPortfolioIdOrderByStartYearDesc(portfolio.getId()).stream()
                .map(EducationResponse::from)
                .toList();
    }

    @Transactional
    public EducationResponse createEducation(UUID userId, EducationRequest request) {
        StudentPortfolio portfolio = findOwnPortfolio(userId);
        validateYears(request);

        Education education = new Education(portfolio, request.degree(), request.institution(), request.startYear());
        education.setFieldOfStudy(request.fieldOfStudy());
        education.setEndYear(request.endYear());
        education.setCurrent(request.current());

        return EducationResponse.from(educationRepository.save(education));
    }

    @Transactional
    public EducationResponse updateEducation(UUID userId, UUID educationId, EducationRequest request) {
        StudentPortfolio portfolio = findOwnPortfolio(userId);
        validateYears(request);

        Education education = findOwnEducation(portfolio.getId(), educationId);
        education.setDegree(request.degree());
        education.setFieldOfStudy(request.fieldOfStudy());
        education.setInstitution(request.institution());
        education.setStartYear(request.startYear());
        education.setEndYear(request.endYear());
        education.setCurrent(request.current());

        return EducationResponse.from(education);
    }

    @Transactional
    public void deleteEducation(UUID userId, UUID educationId) {
        StudentPortfolio portfolio = findOwnPortfolio(userId);
        Education education = findOwnEducation(portfolio.getId(), educationId);
        educationRepository.delete(education);
    }

    private void validateYears(EducationRequest request) {
        if (request.endYear() != null && request.endYear() < request.startYear()) {
            throw new InvalidRequestException("End year must not be before start year");
        }
    }

    private StudentPortfolio findOwnPortfolio(UUID userId) {
        return portfolioRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Student portfolio not found"));
    }

    private Education findOwnEducation(UUID portfolioId, UUID educationId) {
        return educationRepository.findByIdAndPortfolioId(educationId, portfolioId)
                .orElseThrow(() -> new ResourceNotFoundException("Education record not found"));
    }
}

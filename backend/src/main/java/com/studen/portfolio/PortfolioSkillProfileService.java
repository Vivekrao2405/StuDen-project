package com.studen.portfolio;

import com.studen.skill.Skill;
import java.util.LinkedHashSet;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The single source of truth for "what skills does this student have", consumed by
 * {@code com.studen.assessment.AssessmentService} and {@code com.studen.practical.*} to determine
 * assessment eligibility. Always derives from {@link StudentPortfolio#getSkills()} — never a
 * second/parallel skill mechanism (e.g. project technologies), per the product rule that there
 * must be exactly one source of truth for a student's skill profile.
 */
@Service
public class PortfolioSkillProfileService {

    private final StudentPortfolioRepository portfolioRepository;

    public PortfolioSkillProfileService(StudentPortfolioRepository portfolioRepository) {
        this.portfolioRepository = portfolioRepository;
    }

    @Transactional(readOnly = true)
    public StudentSkillProfile resolve(UUID userId) {
        return portfolioRepository.findByUserId(userId)
                .map(portfolio -> new StudentSkillProfile(true,
                        portfolio.getSkills().stream().map(Skill::getId)
                                .collect(Collectors.toCollection(LinkedHashSet::new))))
                .orElseGet(StudentSkillProfile::noPortfolio);
    }
}

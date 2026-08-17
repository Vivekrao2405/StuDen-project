package com.studen.practical;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exposes the latest evaluated practical-attempt score for a (user, skill) pair as additional
 * evidence for the existing skill-evaluation UI — deliberately read-only and additive. This does
 * NOT recompute, own, or feed back into {@code com.studen.assessment.SkillResultService}'s
 * knowledge-assessment readiness calculation (spec §30): 7.4 provides evidence, the existing
 * skill-evaluation architecture stays the sole owner of overall readiness.
 */
@Service
public class PracticalEvidenceService {

    private final PracticalAttemptRepository attemptRepository;

    public PracticalEvidenceService(PracticalAttemptRepository attemptRepository) {
        this.attemptRepository = attemptRepository;
    }

    @Transactional(readOnly = true)
    public Optional<PracticalEvidenceResponse> latestForSkill(UUID userId, UUID skillId) {
        return attemptRepository.findLatestEvaluatedByUserAndSkill(userId, skillId, PageRequest.of(0, 1)).stream()
                .findFirst()
                .map(attempt -> {
                    PracticalAssessment assessment = attempt.getPracticalAssessment();
                    return new PracticalEvidenceResponse(assessment.getSkill().getId(), assessment.getSkill().getName(),
                            assessment.getId(), assessment.getTitle(), attempt.getScore(), attempt.getMaxScore(),
                            attempt.getEvaluatedAt());
                });
    }
}

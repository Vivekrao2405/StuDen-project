package com.studen.skill;

import java.util.List;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Custom skills and the category list — kept separate from {@link SkillSearchService}, which is
 * purely read/ranking concerns.
 */
@Service
public class SkillService {

    // A skill a student typed in themselves has no known brand logo and no specific icon
    // assignment — Sparkles is the same "something, not nothing" fallback SkillIcon already
    // renders client-side for any skill it can't otherwise resolve.
    private static final String DEFAULT_CUSTOM_ICON = "Sparkles";

    private final SkillRepository skillRepository;

    public SkillService(SkillRepository skillRepository) {
        this.skillRepository = skillRepository;
    }

    /**
     * Finds an existing catalog skill by normalized name, or creates one — a shared, growing
     * catalog rather than a portfolio-private one, so the next student who types the same skill
     * (in any casing) finds it instantly instead of creating a duplicate entry.
     */
    @Transactional
    public SkillResponse createOrGetCustomSkill(CreateSkillRequest request) {
        String normalized = normalize(request.name());

        return skillRepository.findByNormalizedName(normalized)
                .map(SkillResponse::from)
                .orElseGet(() -> createNew(request, normalized));
    }

    private SkillResponse createNew(CreateSkillRequest request, String normalized) {
        try {
            Skill skill = new Skill(request.name().trim(), normalized, request.category().trim(),
                    DEFAULT_CUSTOM_ICON, IconType.LUCIDE);
            // Flushed immediately (rather than deferred to transaction commit) so a unique-
            // constraint violation from a concurrent request creating the same skill surfaces
            // here, not after this method has already returned.
            return SkillResponse.from(skillRepository.saveAndFlush(skill));
        } catch (DataIntegrityViolationException e) {
            return skillRepository.findByNormalizedName(normalized)
                    .map(SkillResponse::from)
                    .orElseThrow(() -> e);
        }
    }

    @Transactional(readOnly = true)
    public List<String> listCategories() {
        return skillRepository.findDistinctCategories();
    }

    private String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}

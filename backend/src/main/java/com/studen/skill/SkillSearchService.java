package com.studen.skill;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ranks matches exact > prefix (name) > exact alias > prefix alias > partial, so a short query
 * like "react" surfaces "React" before "React Native" instead of returning them in arbitrary
 * database order. The candidate set itself is filtered in the repository query; this only orders
 * and caps it, since the catalog is small enough that in-memory ranking is simpler than encoding
 * the same priority into SQL.
 */
@Service
public class SkillSearchService {

    private static final int MAX_RESULTS = 8;

    private final SkillRepository skillRepository;

    public SkillSearchService(SkillRepository skillRepository) {
        this.skillRepository = skillRepository;
    }

    @Transactional(readOnly = true)
    public List<SkillResponse> search(String query) {
        String term = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (term.isEmpty()) {
            return List.of();
        }

        return skillRepository.search(term).stream()
                .sorted(Comparator.comparingInt(skill -> rank(skill, term)))
                .limit(MAX_RESULTS)
                .map(SkillResponse::from)
                .toList();
    }

    private int rank(Skill skill, String term) {
        String name = skill.getNormalizedName().toLowerCase(Locale.ROOT);
        if (name.equals(term)) return 0;
        if (name.startsWith(term)) return 1;
        if (skill.getAliases().stream().anyMatch(a -> a.equalsIgnoreCase(term))) return 2;
        if (skill.getAliases().stream().anyMatch(a -> a.toLowerCase(Locale.ROOT).startsWith(term))) return 3;
        if (name.contains(term)) return 4;
        return 5;
    }
}

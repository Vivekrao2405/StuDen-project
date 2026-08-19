package com.studen.portfolio;

import java.util.Set;
import java.util.UUID;

// The one place a "student's skills" is ever resolved from, for assessment-eligibility purposes —
// always derived from StudentPortfolio.skills (the same skills the student explicitly attached to
// their portfolio), never from project technologies or any other secondary signal. Skill
// membership is always by Skill.id, never by name, so "Java" can never match "JavaScript".
public record StudentSkillProfile(boolean hasPortfolio, Set<UUID> skillIds) {

    public static StudentSkillProfile noPortfolio() {
        return new StudentSkillProfile(false, Set.of());
    }

    public boolean hasSkills() {
        return !skillIds.isEmpty();
    }

    public boolean isEligibleFor(UUID skillId) {
        return skillIds.contains(skillId);
    }
}

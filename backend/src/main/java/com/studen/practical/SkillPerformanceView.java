package com.studen.practical;

import java.util.UUID;

// Server-computed skill-level breakdown across an attempt's questions (spec §15) -- grouped by
// each question's effective skill (its own override, or the assessment's skill when unset), summed
// server-side from PracticalAttemptQuestion rows, never trusted from the client. Feeds the
// learning/practice system later; not itself a persisted concept.
public record SkillPerformanceView(UUID skillId, String skillName, int pointsEarned, int pointsPossible, int percentage) {
}

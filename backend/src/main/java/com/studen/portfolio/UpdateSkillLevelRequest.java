package com.studen.portfolio;

import com.studen.skill.SkillLevel;
import jakarta.validation.constraints.NotNull;

public record UpdateSkillLevelRequest(@NotNull(message = "Level is required") SkillLevel level) {
}

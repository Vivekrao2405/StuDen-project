package com.studen.placement;

import com.studen.assessment.AssessmentLevel;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

// One entry in a role required-skill mapping. weight/priority fall back to sensible defaults when
// omitted, so an admin can map a skill with nothing but its id.
public record RoleSkillRequest(

        @NotNull(message = "Skill is required")
        UUID skillId,

        @Min(value = 1, message = "Weight must be at least 1")
        Integer weight,

        AssessmentLevel requiredProficiency,

        @Min(value = 0, message = "Priority cannot be negative")
        Integer priority) {
}

package com.studen.placement;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

// Full replacement of a role required-skill set in one call, which is how an admin screen edits
// a mapping. Adding or removing a single skill has its own endpoints.
public record RoleSkillsRequest(

        @NotNull(message = "Skills are required")
        @Valid
        List<RoleSkillRequest> skills) {
}

package com.studen.showcase;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record ProjectRequest(

        @NotBlank(message = "Project title is required")
        @Size(max = 255, message = "Title must be at most 255 characters")
        String title,

        @Size(max = 500, message = "Short description must be at most 500 characters")
        String shortDescription,

        @Size(max = 5000, message = "Description must be at most 5000 characters")
        String description,

        ProjectVisibility visibility,

        Set<UUID> skillIds,

        @Valid
        List<ProjectLinkRequest> links) {
}

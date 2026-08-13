package com.studen.showcase;

import com.studen.skill.SkillResponse;
import java.util.List;
import java.util.UUID;

/** Compact project card shown in a public profile's Showcase section. */
public record PublicProjectSummaryResponse(
        UUID id, String title, String shortDescription, String coverImageUrl, List<SkillResponse> skills) {

    // `media` is passed explicitly (batch-fetched by the caller for every project on the profile
    // in one query) rather than read through project.getMedia() — reading it per-project here is
    // what turned this into an N+1 on the highest-traffic, unauthenticated public profile page.
    public static PublicProjectSummaryResponse from(Project project, List<ProjectMedia> media) {
        return new PublicProjectSummaryResponse(
                project.getId(),
                project.getTitle(),
                project.getShortDescription(),
                CoverMediaResolver.resolve(media),
                project.getSkills().stream().map(SkillResponse::from).toList());
    }
}

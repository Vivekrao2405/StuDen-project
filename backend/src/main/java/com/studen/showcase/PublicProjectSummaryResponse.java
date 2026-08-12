package com.studen.showcase;

import com.studen.skill.SkillResponse;
import java.util.List;
import java.util.UUID;

/** Compact project card shown in a public profile's Showcase section. */
public record PublicProjectSummaryResponse(
        UUID id, String title, String shortDescription, String coverImageUrl, List<SkillResponse> skills) {

    public static PublicProjectSummaryResponse from(Project project) {
        return new PublicProjectSummaryResponse(
                project.getId(),
                project.getTitle(),
                project.getShortDescription(),
                CoverMediaResolver.resolve(project.getMedia()),
                project.getSkills().stream().map(SkillResponse::from).toList());
    }
}

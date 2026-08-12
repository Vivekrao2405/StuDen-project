package com.studen.showcase;

import com.studen.skill.SkillResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProjectResponse(
        UUID id,
        String title,
        String shortDescription,
        String description,
        ProjectVisibility visibility,
        List<SkillResponse> skills,
        List<ProjectMediaResponse> media,
        List<ProjectLinkResponse> links,
        String coverImageUrl,
        Instant createdAt,
        Instant updatedAt) {

    // `media` is passed explicitly (fetched fresh via ProjectMediaRepository) rather than read
    // through project.getMedia() — that lazy collection can already be initialized (and stale)
    // on the managed Project instance from earlier in the same transaction, e.g. right after a
    // media upload/remove/reorder that wrote through the repository directly.
    public static ProjectResponse from(Project project, List<ProjectMedia> media, String coverImageUrl) {
        return new ProjectResponse(
                project.getId(),
                project.getTitle(),
                project.getShortDescription(),
                project.getDescription(),
                project.getVisibility(),
                project.getSkills().stream().map(SkillResponse::from).toList(),
                media.stream().map(ProjectMediaResponse::from).toList(),
                project.getLinks().stream().map(ProjectLinkResponse::from).toList(),
                coverImageUrl,
                project.getCreatedAt(),
                project.getUpdatedAt());
    }
}

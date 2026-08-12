package com.studen.showcase;

import com.studen.skill.SkillResponse;
import java.util.List;
import java.util.UUID;

/** Full project detail for the public project page — the owning student is identified only by
 * their public slug/name/photo, never their internal user/portfolio id. */
public record PublicProjectDetailResponse(
        UUID id,
        String title,
        String shortDescription,
        String description,
        List<SkillResponse> skills,
        List<ProjectMediaResponse> media,
        List<ProjectLinkResponse> links,
        String studentName,
        String studentProfileImageUrl,
        String studentSlug) {
}

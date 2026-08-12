package com.studen.share;

import com.studen.showcase.PublicProjectSummaryResponse;
import com.studen.skill.SkillResponse;
import java.util.List;

public record PublicProfileResponse(
        String slug,
        String profileUrl,
        String fullName,
        String profileImageUrl,
        String coverImageUrl,
        String headline,
        String about,
        String location,
        boolean availability,
        List<SkillResponse> skills,
        List<PublicEducationItem> education,
        List<PublicCertificateItem> certificates,
        List<PublicProjectSummaryResponse> showcase,
        List<Object> services) {
}

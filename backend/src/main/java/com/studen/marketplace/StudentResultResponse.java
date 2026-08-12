package com.studen.marketplace;

import com.studen.portfolio.StudentPortfolio;
import com.studen.skill.SkillResponse;
import java.util.List;

/**
 * A student-profile marketplace result. Deliberately exposes {@code publicSlug} rather than any
 * internal portfolio/user id — matches the existing public-profile convention — and never
 * includes email, phone, or any other private account information.
 */
public record StudentResultResponse(
        String type,
        String publicSlug,
        String fullName,
        String profileImageUrl,
        String headline,
        String location,
        boolean available,
        List<SkillResponse> skills,
        String bio) implements MarketplaceResultResponse {

    public static StudentResultResponse from(StudentPortfolio portfolio) {
        return new StudentResultResponse(
                "STUDENT",
                portfolio.getPublicSlug(),
                portfolio.getUser().getFullName(),
                portfolio.getUser().getProfileImageUrl(),
                portfolio.getHeadline(),
                portfolio.getLocation(),
                portfolio.isAvailable(),
                portfolio.getSkills().stream().map(SkillResponse::from).toList(),
                portfolio.getBio());
    }
}

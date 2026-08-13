package com.studen.marketplace;

import java.util.Map;

/**
 * Maps a skill's fine-grained catalog category (see backend V3/V4 skill migrations — ~21 values
 * like "Frontend", "Data/Analytics", "Photography & Video") onto one of the broad marketplace
 * categories a student can be filtered by. Students have no category field of their own — only
 * their skills do — so this is how a student's marketplace category is derived.
 *
 * This is a deliberately coarse, disclosed approximation: the skill catalog doesn't distinguish
 * "Marketing" from general "Business" skills, so both currently map to BUSINESS_FINANCE. Any
 * catalog category not listed here (including future additions) falls back to OTHER rather than
 * throwing, so a new skill category never breaks marketplace filtering.
 */
final class SkillCategoryMapper {

    private static final Map<String, MarketplaceCategory> BY_SKILL_CATEGORY = Map.ofEntries(
            Map.entry("Frontend", MarketplaceCategory.TECHNOLOGY),
            Map.entry("Backend", MarketplaceCategory.TECHNOLOGY),
            Map.entry("Programming", MarketplaceCategory.TECHNOLOGY),
            Map.entry("Database", MarketplaceCategory.TECHNOLOGY),
            Map.entry("AI/ML", MarketplaceCategory.TECHNOLOGY),
            Map.entry("Mobile", MarketplaceCategory.TECHNOLOGY),
            Map.entry("DevOps/Cloud", MarketplaceCategory.TECHNOLOGY),
            Map.entry("Cybersecurity", MarketplaceCategory.TECHNOLOGY),
            Map.entry("Design", MarketplaceCategory.DESIGN_CREATIVE),
            Map.entry("Arts & Performance", MarketplaceCategory.ARTS_PERFORMANCE),
            Map.entry("Business", MarketplaceCategory.BUSINESS_FINANCE),
            Map.entry("Writing", MarketplaceCategory.WRITING_CONTENT),
            Map.entry("Teaching", MarketplaceCategory.EDUCATION_TUTORING),
            Map.entry("Academic", MarketplaceCategory.EDUCATION_TUTORING),
            Map.entry("Photography & Video", MarketplaceCategory.VIDEO_MEDIA),
            Map.entry("Media & Content", MarketplaceCategory.VIDEO_MEDIA),
            Map.entry("Data/Analytics", MarketplaceCategory.DATA_ANALYTICS),
            Map.entry("Practical Skills", MarketplaceCategory.PRACTICAL_TECHNICAL),
            Map.entry("Languages", MarketplaceCategory.LANGUAGES),
            Map.entry("Leadership", MarketplaceCategory.OTHER),
            Map.entry("Sports & Fitness", MarketplaceCategory.SPORTS_FITNESS),
            Map.entry("Career Skills", MarketplaceCategory.OTHER));

    private SkillCategoryMapper() {
    }

    static MarketplaceCategory map(String skillCategory) {
        return BY_SKILL_CATEGORY.getOrDefault(skillCategory, MarketplaceCategory.OTHER);
    }
}

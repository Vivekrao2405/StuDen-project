package com.studen.marketplace;

public enum MarketplaceCategory {
    TECHNOLOGY,
    DESIGN_CREATIVE,
    BUSINESS_FINANCE,
    MARKETING,
    WRITING_CONTENT,
    EDUCATION_TUTORING,
    VIDEO_MEDIA,
    DATA_ANALYTICS,
    ENGINEERING,
    // Added in Phase 6.3 for service creation — additive only, no existing constants renamed
    // (this column has no DB CHECK constraint and a student's category is derived live via
    // SkillCategoryMapper, never persisted, so this needed no migration).
    ARTS_PERFORMANCE,
    SPORTS_FITNESS,
    SCIENCE_RESEARCH,
    LANGUAGES,
    PRACTICAL_TECHNICAL,
    OTHER
}

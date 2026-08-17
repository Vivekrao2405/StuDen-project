package com.studen.user;

import com.studen.portfolio.StudentPortfolio;
import java.time.Instant;
import java.util.UUID;

// GET /api/v1/admin/users/{id} — adds fields not needed in the list row. Still excludes
// passwordHash and any token/session data (spec §6).
public record AdminUserDetailResponse(
        UUID id,
        String fullName,
        String email,
        String phone,
        String profileImageUrl,
        String university,
        UserRole role,
        boolean emailVerified,
        AdminUserStatus status,
        Instant createdAt,
        Instant deletedAt,
        String publicSlug,
        Boolean portfolioAvailable) {

    public static AdminUserDetailResponse from(User user, StudentPortfolio portfolio) {
        return new AdminUserDetailResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getProfileImageUrl(),
                user.getUniversity(),
                user.getRole(),
                user.isVerified(),
                AdminUserStatus.of(user),
                user.getCreatedAt(),
                user.getDeletedAt(),
                portfolio != null ? portfolio.getPublicSlug() : null,
                portfolio != null ? portfolio.isAvailable() : null);
    }
}

package com.studen.user;

import java.time.Instant;
import java.util.UUID;

// List-row shape for GET /api/v1/admin/users — deliberately excludes passwordHash and any
// token/session data (spec §5).
public record AdminUserSummaryResponse(
        UUID id,
        String fullName,
        String email,
        String profileImageUrl,
        UserRole role,
        AdminUserStatus status,
        Instant createdAt) {

    public static AdminUserSummaryResponse from(User user) {
        return new AdminUserSummaryResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getProfileImageUrl(),
                user.getRole(),
                AdminUserStatus.of(user),
                user.getCreatedAt());
    }
}

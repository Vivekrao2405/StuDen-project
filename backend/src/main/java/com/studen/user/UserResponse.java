package com.studen.user;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String fullName,
        String email,
        String phone,
        String profileImageUrl,
        String university,
        UserRole role,
        boolean emailVerified,
        boolean active,
        Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getProfileImageUrl(),
                user.getUniversity(),
                user.getRole(),
                user.isVerified(),
                user.isActive(),
                user.getCreatedAt());
    }
}

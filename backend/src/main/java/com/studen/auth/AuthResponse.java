package com.studen.auth;

import java.util.UUID;

public record AuthResponse(
        UUID id,
        String fullName,
        String email,
        boolean emailVerified,
        String accessToken) {
}

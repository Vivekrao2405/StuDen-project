package com.studen.auth;

import com.studen.common.exception.DuplicateEmailException;
import com.studen.common.exception.InvalidCredentialsException;
import com.studen.security.JwtService;
import com.studen.security.LoginAttemptService;
import com.studen.security.RefreshTokenService;
import com.studen.security.RefreshTokenService.IssuedRefreshToken;
import com.studen.security.UserPrincipal;
import com.studen.user.User;
import com.studen.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttemptService;
    private final RefreshTokenService refreshTokenService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
            LoginAttemptService loginAttemptService, RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginAttemptService = loginAttemptService;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional
    public AuthResult register(RegisterRequest request, String userAgent, String ipAddress) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException(request.email());
        }

        User user = new User(request.fullName(), request.email(), passwordEncoder.encode(request.password()));
        user = userRepository.save(user);

        return issueSession(user, userAgent, ipAddress);
    }

    @Transactional
    public AuthResult login(LoginRequest request, String userAgent, String ipAddress) {
        // Checked before touching the database, and with the exact same exception (and message)
        // as a wrong password or unknown email below — a locked-out attacker must not be able to
        // distinguish "locked" from "wrong credentials" any more than they can distinguish
        // "wrong password" from "no such account".
        if (loginAttemptService.isBlocked(request.email())) {
            throw new InvalidCredentialsException();
        }

        User user = userRepository.findByEmail(request.email()).orElse(null);
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            loginAttemptService.recordFailure(request.email());
            throw new InvalidCredentialsException();
        }
        if (!user.isActive()) {
            throw new InvalidCredentialsException();
        }

        loginAttemptService.recordSuccess(request.email());
        return issueSession(user, userAgent, ipAddress);
    }

    /**
     * Exchanges a still-valid refresh token for a new access token, rotating the refresh token in
     * the same step. This is what lets an access token's expiry stay short (minutes, not days)
     * without forcing the user to re-authenticate every time it lapses.
     */
    @Transactional
    public AuthResult refresh(String rawRefreshToken, String userAgent, String ipAddress) {
        IssuedRefreshToken rotated = refreshTokenService.rotate(rawRefreshToken, userAgent, ipAddress);
        return toAuthResult(rotated.user(), rotated.rawToken());
    }

    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }

    private AuthResult issueSession(User user, String userAgent, String ipAddress) {
        IssuedRefreshToken issued = refreshTokenService.issue(user, userAgent, ipAddress);
        return toAuthResult(user, issued.rawToken());
    }

    private AuthResult toAuthResult(User user, String refreshToken) {
        String accessToken = jwtService.generateToken(new UserPrincipal(user));
        AuthResponse response = new AuthResponse(user.getId(), user.getFullName(), user.getEmail(), user.isVerified(), accessToken);
        return new AuthResult(response, refreshToken);
    }
}

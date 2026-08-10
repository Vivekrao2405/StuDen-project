package com.studen.auth;

import com.studen.common.exception.DuplicateEmailException;
import com.studen.common.exception.InvalidCredentialsException;
import com.studen.security.JwtService;
import com.studen.security.LoginAttemptService;
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

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService,
            LoginAttemptService loginAttemptService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.loginAttemptService = loginAttemptService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateEmailException(request.email());
        }

        User user = new User(request.fullName(), request.email(), passwordEncoder.encode(request.password()));
        user = userRepository.save(user);

        return toAuthResponse(user);
    }

    public AuthResponse login(LoginRequest request) {
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

        loginAttemptService.recordSuccess(request.email());
        return toAuthResponse(user);
    }

    private AuthResponse toAuthResponse(User user) {
        String token = jwtService.generateToken(new UserPrincipal(user));
        return new AuthResponse(user.getId(), user.getFullName(), user.getEmail(), user.isVerified(), token);
    }
}

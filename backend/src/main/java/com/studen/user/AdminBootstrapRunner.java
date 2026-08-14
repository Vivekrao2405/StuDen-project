package com.studen.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Promotes one configured account to ADMIN on every startup — the only way to grant the ADMIN
 * role in this codebase (Phase 7.1: there is no in-app promotion UI, deliberately, since only one
 * or two content/admin accounts are expected for the foreseeable future). Idempotent: a no-op
 * once the account is already ADMIN, and a no-op entirely if the env var is unset/blank or no
 * matching user exists yet (e.g. first deploy before that account has registered).
 */
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final UserRepository userRepository;
    private final String bootstrapEmail;

    public AdminBootstrapRunner(UserRepository userRepository,
            @Value("${app.admin.bootstrap-email:}") String bootstrapEmail) {
        this.userRepository = userRepository;
        this.bootstrapEmail = bootstrapEmail;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (bootstrapEmail == null || bootstrapEmail.isBlank()) {
            return;
        }
        userRepository.findByEmail(bootstrapEmail.trim()).ifPresent(user -> {
            if (user.getRole() != UserRole.ADMIN) {
                user.setRole(UserRole.ADMIN);
                log.info("Promoted {} to ADMIN via ADMIN_BOOTSTRAP_EMAIL", user.getEmail());
            }
        });
    }
}

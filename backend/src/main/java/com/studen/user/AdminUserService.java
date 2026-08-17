package com.studen.user;

import com.studen.common.exception.ConflictException;
import com.studen.common.exception.ForbiddenActionException;
import com.studen.common.exception.InvalidRequestException;
import com.studen.common.exception.ResourceNotFoundException;
import com.studen.notification.NotificationPreferenceRepository;
import com.studen.notification.NotificationRepository;
import com.studen.notification.PushSubscriptionRepository;
import com.studen.portfolio.StudentPortfolio;
import com.studen.portfolio.StudentPortfolioRepository;
import com.studen.security.RefreshTokenRevoker;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin-only user account management (search/view/deactivate/restore/permanently delete).
 * Every method here assumes the caller has already been authorized as ADMIN by
 * {@code @PreAuthorize} on {@link AdminUserController} — this class does not re-check role, only
 * business rules (self-action, protected-admin-target, current state).
 *
 * <p>Permanent deletion never removes the {@code users} row (see the anonymization block in
 * {@link #permanentlyDelete}) — {@code conversations}, {@code messages}, {@code work_orders}, and
 * {@code service_requests} all cascade-delete on their user foreign key, so a raw row delete
 * would destroy the *other* party's conversation/order history along with the deleted account.
 * Anonymizing in place avoids that with zero schema risk to those tables.
 */
@Service
public class AdminUserService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String DELETE_CONFIRMATION = "DELETE";

    private final UserRepository userRepository;
    private final StudentPortfolioRepository portfolioRepository;
    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final NotificationRepository notificationRepository;
    private final AdminAuditLogRepository auditLogRepository;
    private final RefreshTokenRevoker refreshTokenRevoker;
    private final PasswordEncoder passwordEncoder;

    public AdminUserService(UserRepository userRepository, StudentPortfolioRepository portfolioRepository,
            PushSubscriptionRepository pushSubscriptionRepository,
            NotificationPreferenceRepository notificationPreferenceRepository,
            NotificationRepository notificationRepository, AdminAuditLogRepository auditLogRepository,
            RefreshTokenRevoker refreshTokenRevoker, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.portfolioRepository = portfolioRepository;
        this.pushSubscriptionRepository = pushSubscriptionRepository;
        this.notificationPreferenceRepository = notificationPreferenceRepository;
        this.notificationRepository = notificationRepository;
        this.auditLogRepository = auditLogRepository;
        this.refreshTokenRevoker = refreshTokenRevoker;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public AdminUserPageResponse<AdminUserSummaryResponse> list(String search, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size), Sort.by(Sort.Direction.DESC, "createdAt"));
        String normalizedSearch = search == null ? "" : search.trim();
        Page<User> result = userRepository.search(normalizedSearch, pageable);
        return AdminUserPageResponse.of(result.map(AdminUserSummaryResponse::from));
    }

    @Transactional(readOnly = true)
    public AdminUserDetailResponse get(UUID targetId) {
        User target = findUserOrThrow(targetId);
        StudentPortfolio portfolio = portfolioRepository.findByUserId(targetId).orElse(null);
        return AdminUserDetailResponse.from(target, portfolio);
    }

    @Transactional
    public void deactivate(UUID adminId, UUID targetId) {
        User target = findUserOrThrow(targetId);
        requireNotSelf(adminId, targetId, "deactivate");
        requireNotProtectedAdmin(target, "deactivated");
        if (!target.isActive()) {
            throw new ConflictException("User is already deactivated");
        }

        target.setActive(false);
        userRepository.save(target);
        refreshTokenRevoker.revokeAllForUser(targetId);
        audit(adminId, targetId, AdminAuditAction.USER_DEACTIVATED);
    }

    @Transactional
    public void restore(UUID adminId, UUID targetId) {
        User target = findUserOrThrow(targetId);
        if (target.getDeletedAt() != null) {
            throw new ConflictException("A permanently deleted account cannot be restored");
        }
        if (target.isActive()) {
            throw new ConflictException("User is already active");
        }

        target.setActive(true);
        userRepository.save(target);
        audit(adminId, targetId, AdminAuditAction.USER_RESTORED);
    }

    @Transactional
    public void permanentlyDelete(UUID adminId, UUID targetId, String confirmation) {
        if (!DELETE_CONFIRMATION.equals(confirmation)) {
            throw new InvalidRequestException("Confirmation text does not match");
        }

        User target = findUserOrThrow(targetId);
        requireNotSelf(adminId, targetId, "delete");
        requireNotProtectedAdmin(target, "deleted");
        if (target.getDeletedAt() != null) {
            throw new ConflictException("User is already deleted");
        }

        anonymize(target);
        // Must be flushed now, not left for end-of-transaction auto-flush: the bulk deletes below
        // (deleteAllByUserId on PushSubscription/NotificationPreference/Notification) are
        // @Modifying(clearAutomatically = true), which calls EntityManager.clear() right after
        // executing — that detaches this still-pending `target` update and silently drops it if
        // it hasn't hit the DB yet.
        userRepository.saveAndFlush(target);
        refreshTokenRevoker.revokeAllForUser(targetId);

        // Cascades at the DB level to Projects/ProjectMedia/ProjectLinks/Education/Certificates/
        // ProfileShares/ProfileCards/ServiceListings/ServiceMedia/ServiceLinks/
        // ServiceDeliverables/service_projects (see V2/V6/V7/V8 migrations) — one delete removes
        // every piece of the user's public showcase/marketplace presence (spec §18/§19).
        portfolioRepository.findByUserId(targetId).ifPresent(portfolioRepository::delete);

        // Single-owner tables — safe to purge outright (spec §20). Conversations, messages, work
        // orders, service requests, assessments, and questions are deliberately left untouched:
        // they keep pointing at the now-anonymized user row, which is exactly what preserves the
        // *other* party's history and historical assessment/question integrity.
        pushSubscriptionRepository.deleteAllByUserId(targetId);
        notificationPreferenceRepository.deleteAllByUserId(targetId);
        notificationRepository.deleteAllByUserId(targetId);

        audit(adminId, targetId, AdminAuditAction.USER_PERMANENTLY_DELETED);
    }

    private void anonymize(User target) {
        target.setFullName("Deleted User");
        // Keeps the unique constraint on email satisfiable without leaking the original address.
        target.setEmail("deleted-" + target.getId() + "@deleted.studen.internal");
        target.setPhone(null);
        // Unusable but well-formed — keeps passwordHash's not-null invariant without leaving any
        // password the account could still be logged into with.
        target.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        target.setProfileImageUrl(null);
        target.setUniversity(null);
        target.setVerified(false);
        target.setActive(false);
        target.setDeletedAt(Instant.now());
    }

    private void requireNotSelf(UUID adminId, UUID targetId, String verb) {
        if (adminId.equals(targetId)) {
            throw new ForbiddenActionException("You cannot " + verb + " your own admin account.");
        }
    }

    private void requireNotProtectedAdmin(User target, String verbPastTense) {
        if (target.getRole() == UserRole.ADMIN) {
            throw new ForbiddenActionException("Admin accounts cannot be " + verbPastTense + " from User Management.");
        }
    }

    private void audit(UUID adminId, UUID targetId, AdminAuditAction action) {
        User admin = userRepository.getReferenceById(adminId);
        User target = userRepository.getReferenceById(targetId);
        auditLogRepository.save(new AdminAuditLog(admin, target, action));
    }

    private User findUserOrThrow(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private int clampSize(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}

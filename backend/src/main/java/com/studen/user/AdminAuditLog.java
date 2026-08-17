package com.studen.user;

import com.studen.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Immutable record of every destructive admin action against a user account (spec §25). Never
 * stores passwords, tokens, or other secrets — just who did what to whom, and when.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "admin_audit_log")
public class AdminAuditLog extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_user_id", nullable = false)
    private User adminUser;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_user_id", nullable = false)
    private User targetUser;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AdminAuditAction action;

    public AdminAuditLog(User adminUser, User targetUser, AdminAuditAction action) {
        this.adminUser = adminUser;
        this.targetUser = targetUser;
        this.action = action;
    }
}

package com.studen.notification;

import com.studen.common.entity.BaseEntity;
import com.studen.user.User;
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
 * One row per (user, type) rather than a fixed set of boolean columns on {@code users} — adding
 * an 8th notification type later needs zero schema change. A missing row means "not yet
 * customized" and resolves to enabled, not "opted out" — see NotificationService.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "notification_preferences")
public class NotificationPreference extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NotificationType type;

    @Column(name = "push_enabled", nullable = false)
    private boolean pushEnabled = true;

    @Column(name = "in_app_enabled", nullable = false)
    private boolean inAppEnabled = true;

    public NotificationPreference(User user, NotificationType type) {
        this.user = user;
        this.type = type;
    }
}

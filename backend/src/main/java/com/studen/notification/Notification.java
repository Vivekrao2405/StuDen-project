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
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "notifications")
public class Notification extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NotificationType type;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "resource_id")
    private UUID resourceId;

    @Column(nullable = false)
    private String url;

    @Column(nullable = false)
    private boolean read = false;

    @Column(name = "read_at")
    private Instant readAt;

    public Notification(User user, NotificationType type, String message, UUID resourceId, String url) {
        this.user = user;
        this.type = type;
        this.message = message;
        this.resourceId = resourceId;
        this.url = url;
    }
}

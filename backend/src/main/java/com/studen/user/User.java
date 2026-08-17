package com.studen.user;

import com.studen.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(unique = true)
    private String phone;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role = UserRole.STUDENT;

    @Column(name = "profile_image_url")
    private String profileImageUrl;

    private String university;

    @Column(name = "is_verified", nullable = false)
    private boolean verified = false;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    // Null unless this account was permanently deleted by an admin (see AdminUserService). At
    // that point `active` is also false and the row is anonymized in place — never physically
    // deleted, since several other tables (conversations, messages, work_orders,
    // service_requests) cascade-delete on their user FK and a raw row delete would destroy the
    // *other* party's history along with it. This column is what distinguishes "deactivated,
    // restorable" from "permanently deleted, not restorable".
    @Column(name = "deleted_at")
    private Instant deletedAt;

    public User(String fullName, String email, String passwordHash) {
        this.fullName = fullName;
        this.email = email;
        this.passwordHash = passwordHash;
    }
}

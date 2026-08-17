package com.studen.user;

/**
 * Derived, never persisted directly — see {@link User#isActive()} / {@link User#getDeletedAt()}.
 * ACTIVE: active=true. DEACTIVATED: active=false, deletedAt=null (restorable).
 * DELETED: active=false, deletedAt!=null (permanent, not restorable).
 */
public enum AdminUserStatus {
    ACTIVE,
    DEACTIVATED,
    DELETED;

    public static AdminUserStatus of(User user) {
        if (user.getDeletedAt() != null) {
            return DELETED;
        }
        return user.isActive() ? ACTIVE : DEACTIVATED;
    }
}

package com.studen.common.exception;

/**
 * A 403 with a caller-supplied message, unlike Spring Security's {@code AccessDeniedException}
 * (thrown by {@code @PreAuthorize} failures), which is deliberately mapped to a single generic
 * message everywhere. This is for business-rule denials that DO need a specific message — e.g.
 * "you cannot delete your own admin account" — where the caller already passed the role check
 * and the 403 is about *which* target, not *who's* calling.
 */
public class ForbiddenActionException extends RuntimeException {

    public ForbiddenActionException(String message) {
        super(message);
    }
}

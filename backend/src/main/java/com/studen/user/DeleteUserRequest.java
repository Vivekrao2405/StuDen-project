package com.studen.user;

// Frontend confirmation is UX only — AdminUserService re-validates this server-side before
// permanently deleting anything (spec §27).
public record DeleteUserRequest(String confirmation) {
}

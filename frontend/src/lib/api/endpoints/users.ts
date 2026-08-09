import { apiFetch } from "@/lib/api/client";
import type { UpdateUserRequest, UserResponse } from "@/lib/api/types";

export function getCurrentUser() {
  return apiFetch<UserResponse>("/users/me");
}

export function updateCurrentUser(payload: UpdateUserRequest) {
  return apiFetch<UserResponse>("/users/me", { method: "PUT", body: payload });
}

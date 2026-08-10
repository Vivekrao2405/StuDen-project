import { apiFetch } from "@/lib/api/client";
import type { UpdateUserRequest, UserResponse } from "@/lib/api/types";

export function getCurrentUser() {
  return apiFetch<UserResponse>("/users/me");
}

export function updateCurrentUser(payload: UpdateUserRequest) {
  return apiFetch<UserResponse>("/users/me", { method: "PUT", body: payload });
}

export function uploadProfileImage(file: File) {
  const formData = new FormData();
  formData.append("file", file);
  return apiFetch<UserResponse>("/users/me/profile-image", { method: "POST", body: formData });
}

export function removeProfileImage() {
  return apiFetch<UserResponse>("/users/me/profile-image", { method: "DELETE" });
}

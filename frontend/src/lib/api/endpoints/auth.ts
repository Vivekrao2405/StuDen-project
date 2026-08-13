import { apiFetch } from "@/lib/api/client";
import type { AuthResponse, LoginRequest, RegisterRequest } from "@/lib/api/types";

export function registerUser(payload: RegisterRequest) {
  return apiFetch<AuthResponse>("/auth/register", { method: "POST", body: payload, auth: false });
}

export function loginUser(payload: LoginRequest) {
  return apiFetch<AuthResponse>("/auth/login", { method: "POST", body: payload, auth: false });
}

// Revokes the refresh-token cookie server-side. `auth: false` because this call authenticates via
// the HttpOnly cookie, not the access token — logout must still succeed even if the access token
// already expired.
export function logoutUser() {
  return apiFetch<void>("/auth/logout", { method: "POST", auth: false });
}

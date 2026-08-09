import { apiFetch } from "@/lib/api/client";
import type { AuthResponse, LoginRequest, RegisterRequest } from "@/lib/api/types";

export function registerUser(payload: RegisterRequest) {
  return apiFetch<AuthResponse>("/auth/register", { method: "POST", body: payload, auth: false });
}

export function loginUser(payload: LoginRequest) {
  return apiFetch<AuthResponse>("/auth/login", { method: "POST", body: payload, auth: false });
}

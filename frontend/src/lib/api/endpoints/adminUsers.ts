import { apiFetch } from "@/lib/api/client";
import type { AdminUserDetail, AdminUserListParams, AdminUserSummary, PageResponse } from "@/lib/api/types";

export function listAdminUsers(params: AdminUserListParams) {
  const query = new URLSearchParams();
  if (params.search) query.set("search", params.search);
  if (params.page !== undefined) query.set("page", String(params.page));
  if (params.size !== undefined) query.set("size", String(params.size));

  const qs = query.toString();
  return apiFetch<PageResponse<AdminUserSummary>>(`/admin/users${qs ? `?${qs}` : ""}`);
}

export function getAdminUser(id: string) {
  return apiFetch<AdminUserDetail>(`/admin/users/${id}`);
}

export function deactivateAdminUser(id: string) {
  return apiFetch<void>(`/admin/users/${id}/deactivate`, { method: "POST" });
}

export function restoreAdminUser(id: string) {
  return apiFetch<void>(`/admin/users/${id}/restore`, { method: "POST" });
}

export function deleteAdminUserPermanently(id: string, confirmation: string) {
  return apiFetch<void>(`/admin/users/${id}`, { method: "DELETE", body: { confirmation } });
}

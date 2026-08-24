import { apiFetch } from "@/lib/api/client";
import type {
  AdminResourceListParams,
  PageResponse,
  ResourceDetail,
  ResourceRequest,
  ResourceSummary,
} from "@/lib/api/resourceTypes";

const BASE = "/admin/resources";

export function listAdminResources(params: AdminResourceListParams) {
  const query = new URLSearchParams();
  if (params.skillId) query.set("skillId", params.skillId);
  if (params.resourceType) query.set("resourceType", params.resourceType);
  if (params.difficulty) query.set("difficulty", params.difficulty);
  if (params.status) query.set("status", params.status);
  if (params.search) query.set("search", params.search);
  if (params.page !== undefined) query.set("page", String(params.page));
  if (params.size !== undefined) query.set("size", String(params.size));

  const qs = query.toString();
  return apiFetch<PageResponse<ResourceSummary>>(`${BASE}${qs ? `?${qs}` : ""}`);
}

export function getAdminResource(id: string) {
  return apiFetch<ResourceDetail>(`${BASE}/${id}`);
}

export function createResource(request: ResourceRequest) {
  return apiFetch<ResourceDetail>(BASE, { method: "POST", body: request });
}

export function updateResource(id: string, request: ResourceRequest) {
  return apiFetch<ResourceDetail>(`${BASE}/${id}`, { method: "PUT", body: request });
}

export function deleteResource(id: string) {
  return apiFetch<void>(`${BASE}/${id}`, { method: "DELETE" });
}

export function publishResource(id: string) {
  return apiFetch<ResourceDetail>(`${BASE}/${id}/publish`, { method: "POST" });
}

export function unpublishResource(id: string) {
  return apiFetch<ResourceDetail>(`${BASE}/${id}/unpublish`, { method: "POST" });
}

export function archiveResource(id: string) {
  return apiFetch<ResourceDetail>(`${BASE}/${id}/archive`, { method: "POST" });
}

export function uploadResourceFile(id: string, file: File) {
  const formData = new FormData();
  formData.append("file", file);
  return apiFetch<ResourceDetail>(`${BASE}/${id}/upload-file`, { method: "POST", body: formData });
}

export function deleteResourceFile(id: string) {
  return apiFetch<ResourceDetail>(`${BASE}/${id}/file`, { method: "DELETE" });
}

import { apiFetch } from "@/lib/api/client";
import type { ServiceMediaResponse, ServiceRequest, ServiceResponse } from "@/lib/api/types";

export function listMyServices() {
  return apiFetch<ServiceResponse[]>("/users/me/services");
}

export function createService(payload: ServiceRequest) {
  return apiFetch<ServiceResponse>("/users/me/services", { method: "POST", body: payload });
}

export function updateService(id: string, payload: ServiceRequest) {
  return apiFetch<ServiceResponse>(`/users/me/services/${id}`, { method: "PUT", body: payload });
}

export function publishService(id: string) {
  return apiFetch<ServiceResponse>(`/users/me/services/${id}/publish`, { method: "POST" });
}

export function deactivateService(id: string) {
  return apiFetch<ServiceResponse>(`/users/me/services/${id}/deactivate`, { method: "POST" });
}

export function reactivateService(id: string) {
  return apiFetch<ServiceResponse>(`/users/me/services/${id}/reactivate`, { method: "POST" });
}

export function setServiceAvailability(id: string, available: boolean) {
  return apiFetch<ServiceResponse>(`/users/me/services/${id}/availability`, {
    method: "PUT",
    body: { available },
  });
}

export function deleteService(id: string) {
  return apiFetch<void>(`/users/me/services/${id}`, { method: "DELETE" });
}

// Returns just the created media item (not the whole service) — same lightweight-response
// pattern used by uploadProjectMedia, so callers can upload several files concurrently and match
// each response back to its source file by id.
export function uploadServiceMedia(serviceId: string, file: File) {
  const formData = new FormData();
  formData.append("file", file);
  return apiFetch<ServiceMediaResponse>(`/users/me/services/${serviceId}/media`, { method: "POST", body: formData });
}

export function removeServiceMedia(serviceId: string, mediaId: string) {
  return apiFetch<ServiceResponse>(`/users/me/services/${serviceId}/media/${mediaId}`, { method: "DELETE" });
}

export function reorderServiceMedia(serviceId: string, mediaIds: string[]) {
  return apiFetch<ServiceResponse>(`/users/me/services/${serviceId}/media/order`, {
    method: "PUT",
    body: { mediaIds },
  });
}

export function setServiceCoverMedia(serviceId: string, mediaId: string) {
  return apiFetch<ServiceResponse>(`/users/me/services/${serviceId}/media/${mediaId}/cover`, { method: "PUT" });
}

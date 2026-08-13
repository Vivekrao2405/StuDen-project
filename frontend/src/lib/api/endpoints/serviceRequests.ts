import { apiFetch } from "@/lib/api/client";
import type { CreateServiceRequestPayload, ServiceRequestRecord } from "@/lib/api/types";

export function createServiceRequest(payload: CreateServiceRequestPayload) {
  return apiFetch<ServiceRequestRecord>("/service-requests", { method: "POST", body: payload });
}

export function listMyServiceRequests() {
  return apiFetch<ServiceRequestRecord[]>("/service-requests/my");
}

export function listIncomingServiceRequests() {
  return apiFetch<ServiceRequestRecord[]>("/service-requests/incoming");
}

export function getServiceRequest(id: string) {
  return apiFetch<ServiceRequestRecord>(`/service-requests/${id}`);
}

export function acceptServiceRequest(id: string) {
  return apiFetch<ServiceRequestRecord>(`/service-requests/${id}/accept`, { method: "POST" });
}

export function rejectServiceRequest(id: string, reason?: string) {
  return apiFetch<ServiceRequestRecord>(`/service-requests/${id}/reject`, {
    method: "POST",
    body: reason ? { reason } : undefined,
  });
}

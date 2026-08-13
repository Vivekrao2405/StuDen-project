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

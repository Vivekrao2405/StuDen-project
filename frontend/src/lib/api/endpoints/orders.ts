import { apiFetch } from "@/lib/api/client";
import type { CancelOrderPayload, OrderResponse, OrderStatus, SubmitWorkPayload } from "@/lib/api/types";

export function getOrCreateOrder(serviceRequestId: string) {
  return apiFetch<OrderResponse>(`/service-requests/${serviceRequestId}/order`, { method: "POST" });
}

export function listMyOrders(status?: OrderStatus) {
  const qs = status ? `?status=${status}` : "";
  return apiFetch<OrderResponse[]>(`/orders${qs}`);
}

export function getOrder(id: string) {
  return apiFetch<OrderResponse>(`/orders/${id}`);
}

export function submitWork(id: string, payload: SubmitWorkPayload) {
  return apiFetch<OrderResponse>(`/orders/${id}/submit`, { method: "POST", body: payload });
}

export function completeOrder(id: string) {
  return apiFetch<OrderResponse>(`/orders/${id}/complete`, { method: "POST" });
}

export function cancelOrder(id: string, payload?: CancelOrderPayload) {
  return apiFetch<OrderResponse>(`/orders/${id}/cancel`, { method: "POST", body: payload });
}

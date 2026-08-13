import { apiFetch } from "@/lib/api/client";
import type { ConversationResponse, ConversationSummaryResponse, CreateMessagePayload, MessageResponse } from "@/lib/api/types";

export function getOrCreateConversation(serviceRequestId: string) {
  return apiFetch<ConversationResponse>(`/service-requests/${serviceRequestId}/conversation`, { method: "POST" });
}

export function listConversations() {
  return apiFetch<ConversationSummaryResponse[]>("/messages/conversations");
}

export function getConversation(id: string) {
  return apiFetch<ConversationResponse>(`/messages/conversations/${id}`);
}

export function listMessages(id: string, before?: string) {
  const query = before ? `?before=${encodeURIComponent(before)}` : "";
  return apiFetch<MessageResponse[]>(`/messages/conversations/${id}/messages${query}`);
}

export function sendMessage(id: string, content: string) {
  const payload: CreateMessagePayload = { content };
  return apiFetch<MessageResponse>(`/messages/conversations/${id}/messages`, { method: "POST", body: payload });
}

export function markConversationRead(id: string) {
  return apiFetch<void>(`/messages/conversations/${id}/read`, { method: "POST" });
}

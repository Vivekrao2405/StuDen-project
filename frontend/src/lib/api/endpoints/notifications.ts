import { apiFetch } from "@/lib/api/client";
import type {
  NotificationPreferenceResponse,
  NotificationResponse,
  NotificationType,
  UnreadCountResponse,
  UpdateNotificationPreferencePayload,
} from "@/lib/api/types";

export function listNotifications(before?: string) {
  const query = before ? `?before=${encodeURIComponent(before)}` : "";
  return apiFetch<NotificationResponse[]>(`/notifications${query}`);
}

export function getUnreadNotificationCount() {
  return apiFetch<UnreadCountResponse>("/notifications/unread-count");
}

export function markNotificationRead(id: string) {
  return apiFetch<void>(`/notifications/${id}/read`, { method: "POST" });
}

export function markAllNotificationsRead() {
  return apiFetch<void>("/notifications/read-all", { method: "POST" });
}

export function getNotificationPreferences() {
  return apiFetch<NotificationPreferenceResponse[]>("/notifications/preferences");
}

export function updateNotificationPreference(type: NotificationType, payload: UpdateNotificationPreferencePayload) {
  return apiFetch<NotificationPreferenceResponse>(`/notifications/preferences/${type}`, {
    method: "PUT",
    body: payload,
  });
}

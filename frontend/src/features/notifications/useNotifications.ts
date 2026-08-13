import { useContext } from "react";

import { NotificationsContext } from "@/features/notifications/notificationsContextDefinition";

export function useNotifications() {
  const ctx = useContext(NotificationsContext);
  if (!ctx) {
    throw new Error("useNotifications must be used within a NotificationsProvider");
  }
  return ctx;
}

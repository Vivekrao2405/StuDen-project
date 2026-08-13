import { createContext } from "react";

export interface NotificationsContextValue {
  unreadCount: number;
  refetch: () => void;
}

export const NotificationsContext = createContext<NotificationsContextValue | null>(null);

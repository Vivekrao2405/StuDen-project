import { createContext } from "react";

export interface UnreadMessagesContextValue {
  unreadCount: number;
  refetch: () => void;
}

export const UnreadMessagesContext = createContext<UnreadMessagesContextValue | null>(null);

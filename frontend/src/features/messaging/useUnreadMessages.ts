import { useContext } from "react";

import { UnreadMessagesContext } from "@/features/messaging/unreadMessagesContextDefinition";

export function useUnreadMessages() {
  const ctx = useContext(UnreadMessagesContext);
  if (!ctx) {
    throw new Error("useUnreadMessages must be used within an UnreadMessagesProvider");
  }
  return ctx;
}

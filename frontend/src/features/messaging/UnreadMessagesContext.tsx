import { useCallback, useEffect, useState, type ReactNode } from "react";

import { useAuth } from "@/features/auth/useAuth";
import { UnreadMessagesContext } from "@/features/messaging/unreadMessagesContextDefinition";
import { listConversations } from "@/lib/api/endpoints/messaging";

/** Fetches the total unread count once when the user becomes authenticated (and resets to 0 on
 * logout) — deliberately no interval polling here, to keep Dashboard/Marketplace load unaffected.
 * `refetch` is called by MessagesPage when it unmounts (i.e. after the user has actually read
 * things), which is the only other time this can go stale. */
export function UnreadMessagesProvider({ children }: { children: ReactNode }) {
  const { status } = useAuth();
  const [unreadCount, setUnreadCount] = useState(0);

  const refetch = useCallback(() => {
    if (status !== "authenticated") return;
    listConversations()
      .then((conversations) => setUnreadCount(conversations.reduce((sum, c) => sum + c.unreadCount, 0)))
      .catch(() => {
        // Best-effort only — a stale/failed badge count isn't worth surfacing an error for.
      });
  }, [status]);

  useEffect(() => {
    if (status === "authenticated") {
      refetch();
    } else {
      setUnreadCount(0);
    }
    // Only re-run when auth status itself changes, not on every refetch identity change.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [status]);

  return <UnreadMessagesContext.Provider value={{ unreadCount, refetch }}>{children}</UnreadMessagesContext.Provider>;
}

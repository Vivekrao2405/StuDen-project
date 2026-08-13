import { useCallback, useEffect, useState, type ReactNode } from "react";

import { useAuth } from "@/features/auth/useAuth";
import { NotificationsContext } from "@/features/notifications/notificationsContextDefinition";
import { getUnreadNotificationCount } from "@/lib/api/endpoints/notifications";

/** Same shape as UnreadMessagesProvider: fetches the unread notification count once when the
 * user becomes authenticated, resets to 0 on logout, and exposes `refetch` for NotificationsPage
 * to call after the user actually reads things — deliberately no interval polling. */
export function NotificationsProvider({ children }: { children: ReactNode }) {
  const { status } = useAuth();
  const [unreadCount, setUnreadCount] = useState(0);

  const refetch = useCallback(() => {
    if (status !== "authenticated") return;
    getUnreadNotificationCount()
      .then((response) => setUnreadCount(response.count))
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
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [status]);

  return <NotificationsContext.Provider value={{ unreadCount, refetch }}>{children}</NotificationsContext.Provider>;
}

import { Bell, CheckCheck } from "lucide-react";
import { useEffect } from "react";
import { useNavigate } from "react-router-dom";

import { Button } from "@/components/ui/button";
import { EmptyState } from "@/components/shared/EmptyState";
import { ErrorState } from "@/components/shared/ErrorState";
import { LoadingState } from "@/components/shared/LoadingState";
import { useNotifications } from "@/features/notifications/useNotifications";
import { listNotifications, markAllNotificationsRead, markNotificationRead } from "@/lib/api/endpoints/notifications";
import { useAsync } from "@/lib/hooks/useAsync";
import { cn } from "@/lib/utils";

function timeAgo(iso: string) {
  const diffMs = Date.now() - new Date(iso).getTime();
  const minutes = Math.floor(diffMs / 60000);
  if (minutes < 1) return "Just now";
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  const days = Math.floor(hours / 24);
  return `${days}d ago`;
}

export function NotificationsPage() {
  const navigate = useNavigate();
  const unread = useNotifications();
  const { data: notifications, error, loading, refetch } = useAsync(() => listNotifications(), []);

  // Refreshes the nav badge once when leaving the page — same targeted-refresh idiom as
  // MessagesPage/UnreadMessagesProvider rather than background polling.
  useEffect(() => {
    return () => unread.refetch();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function handleOpen(id: string, url: string, read: boolean) {
    navigate(url);
    if (!read) {
      try {
        await markNotificationRead(id);
      } catch {
        // Best-effort — a failed mark-read shouldn't block navigation the user already triggered.
      }
    }
  }

  async function handleMarkAllRead() {
    try {
      await markAllNotificationsRead();
      refetch();
      unread.refetch();
    } catch {
      // Best-effort only.
    }
  }

  if (loading) {
    return <LoadingState label="Loading notifications..." />;
  }

  if (error) {
    return <ErrorState message={error.message} onRetry={refetch} />;
  }

  return (
    <div className="mx-auto max-w-2xl space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-foreground">Notifications</h1>
          <p className="text-sm text-muted-foreground">Updates about your requests, orders and messages.</p>
        </div>
        {notifications && notifications.some((n) => !n.read) ? (
          <Button size="sm" variant="outline" onClick={handleMarkAllRead}>
            <CheckCheck className="size-4" /> Mark all read
          </Button>
        ) : null}
      </div>

      {notifications && notifications.length > 0 ? (
        <div className="space-y-2">
          {notifications.map((notification) => (
            <button
              key={notification.id}
              type="button"
              onClick={() => handleOpen(notification.id, notification.url, notification.read)}
              className={cn(
                "flex w-full items-start gap-3 rounded-xl border border-border px-4 py-3 text-left transition hover:bg-muted/50",
                !notification.read && "bg-primary/5"
              )}
            >
              {!notification.read ? <span className="mt-1.5 size-2 shrink-0 rounded-full bg-primary" /> : <span className="mt-1.5 size-2 shrink-0" />}
              <div className="min-w-0 flex-1">
                <p className={cn("text-sm text-foreground", !notification.read && "font-medium")}>{notification.message}</p>
                <p className="mt-0.5 text-xs text-muted-foreground">{timeAgo(notification.createdAt)}</p>
              </div>
            </button>
          ))}
        </div>
      ) : (
        <EmptyState icon={Bell} title="No notifications yet" description="Updates about your activity will show up here." />
      )}
    </div>
  );
}

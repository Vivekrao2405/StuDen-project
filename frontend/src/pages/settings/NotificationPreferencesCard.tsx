import { useState } from "react";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Separator } from "@/components/ui/separator";
import { Switch } from "@/components/ui/switch";
import { useEnablePush } from "@/features/notifications/useEnablePush";
import { isPushSupported } from "@/features/notifications/pushSupport";
import { useToast } from "@/hooks/useToast";
import { getNotificationPreferences, updateNotificationPreference } from "@/lib/api/endpoints/notifications";
import { useAsync } from "@/lib/hooks/useAsync";
import type { NotificationType } from "@/lib/api/types";

type PushPermission = NotificationPermission | "unsupported";

// Mirrors sw.ts's resourceCategory grouping — a user thinks in terms of "messages / requests /
// orders", not 7 individual notification types, so toggling one category updates every type
// underneath it together. Matches the spec's mockup (Push Notifications / Messages / Service
// Requests / Orders) rather than exposing all 7 types as separate rows.
const CATEGORIES: { key: string; label: string; types: NotificationType[] }[] = [
  { key: "MESSAGE", label: "Messages", types: ["NEW_MESSAGE"] },
  { key: "REQUEST", label: "Service Requests", types: ["NEW_SERVICE_REQUEST", "REQUEST_ACCEPTED", "REQUEST_REJECTED"] },
  { key: "ORDER", label: "Orders", types: ["WORK_SUBMITTED", "ORDER_COMPLETED", "ORDER_CANCELLED"] },
];

export function NotificationPreferencesCard() {
  const { data: preferences, loading, refetch } = useAsync(() => getNotificationPreferences(), []);
  const { enablePush, enabling } = useEnablePush();
  const toast = useToast();
  const [permission, setPermission] = useState<PushPermission>(
    isPushSupported() ? Notification.permission : "unsupported"
  );
  const [updatingCategory, setUpdatingCategory] = useState<string | null>(null);

  function categoryEnabled(types: NotificationType[]) {
    if (!preferences) return true;
    return types.some((type) => preferences.find((p) => p.type === type)?.pushEnabled ?? true);
  }

  async function handleCategoryToggle(category: (typeof CATEGORIES)[number], enabled: boolean) {
    setUpdatingCategory(category.key);
    try {
      await Promise.all(
        category.types.map((type) => {
          const existing = preferences?.find((p) => p.type === type);
          return updateNotificationPreference(type, {
            pushEnabled: enabled,
            inAppEnabled: existing?.inAppEnabled ?? true,
          });
        })
      );
      refetch();
    } catch {
      toast.error("Couldn't update your preference. Please try again.");
    } finally {
      setUpdatingCategory(null);
    }
  }

  async function handleEnable() {
    const result = await enablePush();
    if (result === "granted") {
      setPermission("granted");
      toast.success("Notifications enabled.");
    } else if (result === "denied") {
      setPermission("denied");
    } else if (result === "error") {
      toast.error("Couldn't enable notifications. Please try again.");
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Notifications</CardTitle>
        <CardDescription>Choose what StuDen can notify you about.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="flex items-center justify-between gap-4">
          <div>
            <p className="text-sm font-medium text-foreground">Push Notifications</p>
            <p className="text-sm text-muted-foreground">
              {permission === "granted"
                ? "Enabled"
                : permission === "denied"
                  ? "Blocked by browser"
                  : permission === "unsupported"
                    ? "Not supported in this browser"
                    : "Not enabled"}
            </p>
          </div>
          {permission === "default" ? (
            <Button size="sm" variant="outline" onClick={handleEnable} disabled={enabling}>
              {enabling ? "Enabling..." : "Enable"}
            </Button>
          ) : null}
          {permission === "denied" ? (
            <p className="max-w-[14rem] text-right text-xs text-muted-foreground">
              Notifications are blocked in this browser's site settings — StuDen can't turn them back on for you.
            </p>
          ) : null}
        </div>

        {permission === "granted" ? (
          <>
            <Separator />
            {loading ? (
              <p className="text-sm text-muted-foreground">Loading preferences...</p>
            ) : (
              <div className="space-y-3">
                {CATEGORIES.map((category) => (
                  <div key={category.key} className="flex items-center justify-between">
                    <p className="text-sm font-medium text-foreground">{category.label}</p>
                    <Switch
                      checked={categoryEnabled(category.types)}
                      onCheckedChange={(checked) => handleCategoryToggle(category, checked === true)}
                      disabled={updatingCategory === category.key}
                    />
                  </div>
                ))}
              </div>
            )}
          </>
        ) : null}
      </CardContent>
    </Card>
  );
}

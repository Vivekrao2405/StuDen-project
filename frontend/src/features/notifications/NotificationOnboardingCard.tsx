import { Bell } from "lucide-react";
import { useState } from "react";

import { Button } from "@/components/ui/button";
import { Card, CardAction, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { useAuth } from "@/features/auth/useAuth";
import { useEnablePush } from "@/features/notifications/useEnablePush";
import { isPushSupported } from "@/features/notifications/pushSupport";
import { useToast } from "@/hooks/useToast";

const DISMISS_KEY = "studen.pushOnboardingDismissed";

/** Shown once per session (not permanently) whenever an authenticated user's browser permission
 * is still "default" — never auto-prompts; the native permission dialog only ever fires from the
 * "Enable Notifications" click below. Session-scoped dismissal (sessionStorage, not localStorage)
 * so declining doesn't permanently hide the option — it can still be reached from
 * Settings → Notifications at any time. */
export function NotificationOnboardingCard() {
  const { status } = useAuth();
  const { enablePush, enabling } = useEnablePush();
  const toast = useToast();
  const [dismissed, setDismissed] = useState(() => sessionStorage.getItem(DISMISS_KEY) === "true");

  const permission = isPushSupported() ? Notification.permission : "unsupported";

  if (status !== "authenticated" || dismissed || permission !== "default") {
    return null;
  }

  function dismiss() {
    sessionStorage.setItem(DISMISS_KEY, "true");
    setDismissed(true);
  }

  async function handleEnable() {
    const result = await enablePush();
    if (result === "granted") {
      toast.success("Notifications enabled.");
      dismiss();
    } else if (result === "denied") {
      toast.info("Notifications weren't enabled. You can turn them on later in Settings.");
      dismiss();
    } else if (result === "error") {
      toast.error("Couldn't enable notifications. Please try again.");
    }
  }

  return (
    <Card size="sm" className="border-primary/20 bg-primary/5">
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          <Bell className="size-4 text-primary" /> Stay updated with StuDen
        </CardTitle>
        <CardDescription>
          Get notified when someone requests your service, sends a message, accepts a request, or updates an order.
        </CardDescription>
        <CardAction className="flex gap-2">
          <Button size="sm" variant="ghost" onClick={dismiss}>
            Not Now
          </Button>
          <Button size="sm" onClick={handleEnable} disabled={enabling}>
            {enabling ? "Enabling..." : "Enable Notifications"}
          </Button>
        </CardAction>
      </CardHeader>
    </Card>
  );
}

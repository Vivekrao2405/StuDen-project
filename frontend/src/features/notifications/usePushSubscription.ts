import { useEffect } from "react";

import { useAuth } from "@/features/auth/useAuth";
import { registerPushSubscription } from "@/lib/api/endpoints/pushSubscriptions";
import { isPushSupported, subscriptionToPayload } from "@/features/notifications/pushSupport";

// Maintains an already-granted push subscription — re-registers it with the backend whenever the
// authenticated user changes (covers a new user logging into a device that was already granted
// permission from a previous session: the endpoint gets re-associated via the backend's
// upsert-by-endpoint behavior). Never prompts for permission itself — that only ever happens from
// an explicit user gesture, see useEnablePush.
export function usePushSubscription() {
  const { status } = useAuth();

  useEffect(() => {
    if (status !== "authenticated" || !isPushSupported() || Notification.permission !== "granted") {
      return;
    }

    let cancelled = false;

    (async () => {
      try {
        const registration = await navigator.serviceWorker.ready;
        const existing = await registration.pushManager.getSubscription();
        if (!existing || cancelled) return;
        await registerPushSubscription(subscriptionToPayload(existing));
      } catch {
        // Best-effort safety net only — a failure here shouldn't disrupt the rest of the app.
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [status]);
}

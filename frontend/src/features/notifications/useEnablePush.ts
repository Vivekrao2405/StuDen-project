import { useState } from "react";

import { getVapidPublicKey, registerPushSubscription } from "@/lib/api/endpoints/pushSubscriptions";
import { isPushSupported, subscriptionToPayload, urlBase64ToUint8Array } from "@/features/notifications/pushSupport";

export type EnablePushResult = "granted" | "denied" | "unsupported" | "error";

// The single explicit-user-gesture flow: request permission, subscribe if needed, register with
// the backend. Shared by the onboarding card and the Settings page so there's exactly one place
// that ever calls Notification.requestPermission().
export function useEnablePush() {
  const [enabling, setEnabling] = useState(false);

  async function enablePush(): Promise<EnablePushResult> {
    if (!isPushSupported()) {
      return "unsupported";
    }

    setEnabling(true);
    try {
      const permission = await Notification.requestPermission();
      if (permission !== "granted") {
        return "denied";
      }

      const registration = await navigator.serviceWorker.ready;
      let subscription = await registration.pushManager.getSubscription();
      if (!subscription) {
        const { publicKey } = await getVapidPublicKey();
        subscription = await registration.pushManager.subscribe({
          userVisibleOnly: true,
          applicationServerKey: urlBase64ToUint8Array(publicKey),
        });
      }

      await registerPushSubscription(subscriptionToPayload(subscription));
      return "granted";
    } catch {
      return "error";
    } finally {
      setEnabling(false);
    }
  }

  return { enablePush, enabling };
}

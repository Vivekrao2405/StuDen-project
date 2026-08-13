import type { RegisterPushSubscriptionPayload } from "@/lib/api/types";

export function isPushSupported() {
  return (
    typeof window !== "undefined" &&
    "serviceWorker" in navigator &&
    "PushManager" in window &&
    typeof Notification !== "undefined"
  );
}

// PushManager.subscribe's applicationServerKey wants a Uint8Array backed by a real ArrayBuffer
// (not the wider ArrayBufferLike Uint8Array.from(...) infers), but the VAPID public key comes
// from the backend as a base64url string.
export function urlBase64ToUint8Array(base64Url: string): Uint8Array<ArrayBuffer> {
  const padding = "=".repeat((4 - (base64Url.length % 4)) % 4);
  const base64 = (base64Url + padding).replace(/-/g, "+").replace(/_/g, "/");
  const rawData = window.atob(base64);
  const output = new Uint8Array(rawData.length);
  for (let i = 0; i < rawData.length; i++) {
    output[i] = rawData.charCodeAt(i);
  }
  return output;
}

// The reverse direction: PushSubscription's keys come back as ArrayBuffers, but the backend
// (and the browser's own subscribe endpoint) expects base64url strings.
function arrayBufferToBase64Url(buffer: ArrayBuffer | null): string {
  if (!buffer) return "";
  const bytes = new Uint8Array(buffer);
  let binary = "";
  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte);
  });
  return window.btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

export function subscriptionToPayload(subscription: PushSubscription): RegisterPushSubscriptionPayload {
  return {
    endpoint: subscription.endpoint,
    p256dh: arrayBufferToBase64Url(subscription.getKey("p256dh")),
    auth: arrayBufferToBase64Url(subscription.getKey("auth")),
  };
}

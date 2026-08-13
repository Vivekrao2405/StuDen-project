import { apiFetch } from "@/lib/api/client";
import type {
  PushSubscriptionResponse,
  RegisterPushSubscriptionPayload,
  VapidPublicKeyResponse,
} from "@/lib/api/types";

export function getVapidPublicKey() {
  return apiFetch<VapidPublicKeyResponse>("/push/vapid-public-key");
}

export function registerPushSubscription(payload: RegisterPushSubscriptionPayload) {
  return apiFetch<PushSubscriptionResponse>("/push/subscriptions", { method: "POST", body: payload });
}

export function unregisterPushSubscription(endpoint: string) {
  return apiFetch<void>(`/push/subscriptions?endpoint=${encodeURIComponent(endpoint)}`, { method: "DELETE" });
}

import { PushNavigationBridge } from "@/features/notifications/PushNavigationBridge";
import { usePushSubscription } from "@/features/notifications/usePushSubscription";

/** Wires up the invisible push-notification plumbing at the app root: maintains an
 * already-granted subscription (usePushSubscription) and bridges service-worker notification
 * clicks back into React Router (PushNavigationBridge). Renders nothing itself. */
export function NotificationRuntime() {
  usePushSubscription();
  return <PushNavigationBridge />;
}

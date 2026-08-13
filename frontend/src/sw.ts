/// <reference lib="webworker" />
/// <reference types="vite-plugin-pwa/client" />

import { clientsClaim } from "workbox-core";
import { cleanupOutdatedCaches, precacheAndRoute } from "workbox-precaching";
import { registerRoute } from "workbox-routing";
import { NetworkFirst } from "workbox-strategies";

declare const self: ServiceWorkerGlobalScope;

// Everything in this block replaces what vite-plugin-pwa's generateSW strategy used to do
// implicitly — injectManifest requires writing it out explicitly. Precaching, skipWaiting,
// clientsClaim, cleanupOutdatedCaches, and the NetworkFirst navigation rule (see its own comment
// below) must all stay byte-for-byte equivalent to the previous behavior; only the two listeners
// further down (push, notificationclick) are new.
precacheAndRoute(self.__WB_MANIFEST);
cleanupOutdatedCaches();
self.skipWaiting();
clientsClaim();

// Every launch tries the network for the current index.html first (which always reflects the
// live deployment), only falling back to the cached copy when offline — so a stale worker can
// never strand a user on a blank screen after a deploy, while offline support is preserved. Same
// cache name as before this migration, so the existing cache isn't orphaned.
registerRoute(
  ({ request }) => request.mode === "navigate",
  new NetworkFirst({
    cacheName: "studen-navigations",
    networkTimeoutSeconds: 4,
  }),
);

// ---------------------------------------------------------------------------------------------
// Push notifications
// ---------------------------------------------------------------------------------------------

interface PushPayload {
  title: string;
  body: string;
  type: string;
  resourceId: string;
  url: string;
  tag: string;
}

interface FocusedResource {
  type: string;
  resourceId: string;
}

// NEW_SERVICE_REQUEST/REQUEST_ACCEPTED/REQUEST_REJECTED all deep-link to the same
// /requests/{id} page, and WORK_SUBMITTED/ORDER_COMPLETED/ORDER_CANCELLED all deep-link to the
// same /orders/{id} page — a page can only announce one focused "type" at a time, so suppression
// must match on which page (category), not the exact notification type. Mirrors
// NotificationUrlBuilder's grouping on the backend.
function resourceCategory(type: string): string {
  if (type === "NEW_SERVICE_REQUEST" || type === "REQUEST_ACCEPTED" || type === "REQUEST_REJECTED") {
    return "REQUEST";
  }
  if (type === "WORK_SUBMITTED" || type === "ORDER_COMPLETED" || type === "ORDER_CANCELLED") {
    return "ORDER";
  }
  return "MESSAGE";
}

// In-memory only, deliberately — it only needs to survive as long as this worker instance is
// serving an open tab. A fresh worker defaulting to "nothing focused" (never suppress) is the
// safe failure mode if this is ever stale.
let focusedResource: FocusedResource | null = null;

self.addEventListener("message", (event: ExtendableMessageEvent) => {
  const data = event.data as { type?: string; resource?: FocusedResource | null } | undefined;
  if (data?.type === "FOCUS") {
    focusedResource = data.resource ?? null;
  }
});

self.addEventListener("push", (event: PushEvent) => {
  if (!event.data) return;
  event.waitUntil(handlePush(event.data.json() as PushPayload));
});

async function handlePush(payload: PushPayload) {
  if (await shouldSuppress(payload)) {
    return;
  }

  await self.registration.showNotification(payload.title, {
    body: payload.body,
    // Stable, unhashed filenames already served from public/ — no new assets needed.
    icon: "/pwa-192x192.png",
    badge: "/pwa-192x192.png",
    // Multiple pushes about the same resource (e.g. two rapid messages in one conversation)
    // collapse into a single visible notification instead of stacking.
    tag: payload.tag,
    data: { url: payload.url, type: payload.type, resourceId: payload.resourceId },
  });
}

// If the user is actively looking at the exact resource this push is about, showing a native
// notification on top of the in-app UI they're already seeing would be a redundant duplicate.
// Re-verified against actually-visible/focused clients (not just the in-memory flag) as a
// defense against a stale value if a tab closed without its FOCUS:null message ever arriving.
async function shouldSuppress(payload: PushPayload): Promise<boolean> {
  if (
    !focusedResource ||
    focusedResource.type !== resourceCategory(payload.type) ||
    focusedResource.resourceId !== payload.resourceId
  ) {
    return false;
  }

  const clientsList = (await self.clients.matchAll({ type: "window", includeUncontrolled: true })) as WindowClient[];
  return clientsList.some((client) => client.visibilityState === "visible" && client.focused);
}

self.addEventListener("notificationclick", (event: NotificationEvent) => {
  event.notification.close();

  const data = event.notification.data as { url?: string } | undefined;
  const url = data?.url;
  // Only ever navigate to a same-origin relative path built server-side by
  // NotificationUrlBuilder — never an arbitrary external URL from notification data.
  if (!url || !url.startsWith("/")) {
    return;
  }

  event.waitUntil(focusOrOpen(url));
});

async function focusOrOpen(url: string) {
  const clientsList = (await self.clients.matchAll({ type: "window", includeUncontrolled: true })) as WindowClient[];
  const existing = clientsList.find((client) => new URL(client.url).origin === self.location.origin);

  if (existing) {
    // postMessage (not client.navigate()) so the already-running React app does a soft
    // client-side route change via react-router instead of a hard page reload that would lose
    // in-memory app state.
    existing.postMessage({ type: "NAVIGATE", url });
    await existing.focus();
    return;
  }

  await self.clients.openWindow(url);
}

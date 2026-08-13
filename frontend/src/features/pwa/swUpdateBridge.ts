// Bridges vite-plugin-pwa's registerSW() call (main.tsx, outside the React tree) to
// ServiceWorkerUpdateBanner (inside it) — same window-event pattern already used for
// UNAUTHORIZED_EVENT in lib/api/client.ts.
export const SW_UPDATE_AVAILABLE_EVENT = "studen:sw-update-available";

let pendingUpdate: (() => void) | null = null;

export function setPendingUpdate(apply: () => void) {
  pendingUpdate = apply;
}

export function applyPendingUpdate() {
  pendingUpdate?.();
}

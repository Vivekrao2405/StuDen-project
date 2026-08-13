import { RefreshCw, X } from "lucide-react";
import { useEffect, useState } from "react";

import { Button } from "@/components/ui/button";
import { applyPendingUpdate, SW_UPDATE_AVAILABLE_EVENT } from "@/features/pwa/swUpdateBridge";

const DISMISS_KEY = "studen.swUpdateDismissed";

/** A new deploy means a new service worker, but it must never yank the page out from under a
 * user mid-session (see vite.config.ts's registerType comment for why that used to happen and
 * could look like an unexplained logout). The new worker installs and waits; this banner is the
 * only thing that ever tells it to take over, via an explicit click. */
export function ServiceWorkerUpdateBanner() {
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    function handleUpdateAvailable() {
      if (sessionStorage.getItem(DISMISS_KEY) !== "true") {
        setVisible(true);
      }
    }
    window.addEventListener(SW_UPDATE_AVAILABLE_EVENT, handleUpdateAvailable);
    return () => window.removeEventListener(SW_UPDATE_AVAILABLE_EVENT, handleUpdateAvailable);
  }, []);

  if (!visible) return null;

  function dismiss() {
    sessionStorage.setItem(DISMISS_KEY, "true");
    setVisible(false);
  }

  return (
    <div className="fixed inset-x-4 bottom-4 z-50 flex items-center justify-between gap-3 rounded-xl border border-border bg-card px-4 py-3 text-sm shadow-lg sm:inset-x-auto sm:right-4 sm:left-auto sm:max-w-sm">
      <p className="text-foreground">A new version of StuDen is available.</p>
      <div className="flex shrink-0 items-center gap-1">
        <Button size="sm" onClick={applyPendingUpdate}>
          <RefreshCw className="size-3.5" /> Refresh
        </Button>
        <button
          type="button"
          onClick={dismiss}
          aria-label="Dismiss"
          className="rounded-full p-1.5 text-muted-foreground hover:bg-muted"
        >
          <X className="size-4" />
        </button>
      </div>
    </div>
  );
}

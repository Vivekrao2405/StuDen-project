import { useCallback, useEffect, useRef, type ReactNode } from "react";

import {
  FocusedResourceContext,
  type FocusedResource,
} from "@/features/notifications/focusedResourceContextDefinition";

/** Tells the service worker which resource (conversation/request/order) the user is currently
 * looking at, so a matching push notification can be suppressed instead of duplicating what's
 * already visible in the UI. Re-sent on visibilitychange since a backgrounded tab must not keep
 * suppressing pushes for a resource the user can no longer see. */
export function FocusedResourceProvider({ children }: { children: ReactNode }) {
  const currentRef = useRef<FocusedResource | null>(null);

  const postFocus = useCallback((resource: FocusedResource | null) => {
    navigator.serviceWorker?.controller?.postMessage({ type: "FOCUS", resource });
  }, []);

  const setFocusedResource = useCallback(
    (resource: FocusedResource | null) => {
      currentRef.current = resource;
      if (document.visibilityState === "visible") {
        postFocus(resource);
      }
    },
    [postFocus]
  );

  useEffect(() => {
    function handleVisibilityChange() {
      postFocus(document.visibilityState === "visible" ? currentRef.current : null);
    }
    document.addEventListener("visibilitychange", handleVisibilityChange);
    return () => document.removeEventListener("visibilitychange", handleVisibilityChange);
  }, [postFocus]);

  return (
    <FocusedResourceContext.Provider value={{ setFocusedResource }}>{children}</FocusedResourceContext.Provider>
  );
}

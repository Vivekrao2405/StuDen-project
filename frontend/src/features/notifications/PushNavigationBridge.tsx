import { useEffect } from "react";
import { useNavigate } from "react-router-dom";

/** Bridges the service worker's notificationclick handler back into the running React app: the
 * worker posts {type:"NAVIGATE", url} instead of doing a hard client.navigate() reload, so an
 * already-open tab gets a normal client-side route change instead of losing in-memory state.
 * Renders nothing — must be mounted inside BrowserRouter to use useNavigate(). */
export function PushNavigationBridge() {
  const navigate = useNavigate();

  useEffect(() => {
    if (!("serviceWorker" in navigator)) return;

    function handleMessage(event: MessageEvent) {
      const data = event.data as { type?: string; url?: string } | undefined;
      if (data?.type === "NAVIGATE" && data.url) {
        navigate(data.url);
      }
    }

    navigator.serviceWorker.addEventListener("message", handleMessage);
    return () => navigator.serviceWorker.removeEventListener("message", handleMessage);
  }, [navigate]);

  return null;
}

import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import { registerSW } from "virtual:pwa-register";

import "./index.css";
import App from "./App.tsx";
import { ToastProvider } from "@/components/shared/ToastProvider";
import { AuthProvider } from "@/features/auth/AuthContext";
import { UnreadMessagesProvider } from "@/features/messaging/UnreadMessagesContext";
import { FocusedResourceProvider } from "@/features/notifications/FocusedResourceContext";
import { NotificationRuntime } from "@/features/notifications/NotificationRuntime";
import { NotificationsProvider } from "@/features/notifications/NotificationsContext";
import { ServiceWorkerUpdateBanner } from "@/features/pwa/ServiceWorkerUpdateBanner";
import { setPendingUpdate, SW_UPDATE_AVAILABLE_EVENT } from "@/features/pwa/swUpdateBridge";

const updateServiceWorker = registerSW({
  immediate: true,
  // registerType: "prompt" (see vite.config.ts) means this fires instead of an automatic
  // reload — the new worker is already installed and waiting, but sits there until the user
  // explicitly asks for it via ServiceWorkerUpdateBanner's "Refresh" button. Checking for an
  // update whenever the app regains focus, plus hourly while it stays open, still closes the gap
  // to the default ~24h lag before the browser would notice a new deployment on its own — it just
  // no longer forces anything on the user once found.
  onNeedRefresh() {
    setPendingUpdate(() => updateServiceWorker());
    window.dispatchEvent(new Event(SW_UPDATE_AVAILABLE_EVENT));
  },
  onRegisteredSW(_swUrl, registration) {
    if (!registration) return;
    window.setInterval(() => registration.update(), 60 * 60 * 1000);
    document.addEventListener("visibilitychange", () => {
      if (document.visibilityState === "visible") registration.update();
    });
  },
});

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <UnreadMessagesProvider>
          <NotificationsProvider>
            <FocusedResourceProvider>
              <ToastProvider>
                <NotificationRuntime />
                <ServiceWorkerUpdateBanner />
                <App />
              </ToastProvider>
            </FocusedResourceProvider>
          </NotificationsProvider>
        </UnreadMessagesProvider>
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>
);

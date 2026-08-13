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

registerSW({
  immediate: true,
  // registerType: "autoUpdate" already reloads the page automatically once a newer service
  // worker takes over (default behavior when onNeedReload isn't overridden — see
  // node_modules/vite-plugin-pwa/dist/client/build/register.js). That only fires once the
  // browser has actually noticed a new deployment, which by default can lag up to ~24h behind.
  // Checking for an update whenever the app regains focus, plus hourly while it stays open,
  // closes most of that gap so an open tab/installed PWA picks up a new deploy promptly instead
  // of drifting further out of sync with the assets the server actually has.
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
                <App />
              </ToastProvider>
            </FocusedResourceProvider>
          </NotificationsProvider>
        </UnreadMessagesProvider>
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>
);

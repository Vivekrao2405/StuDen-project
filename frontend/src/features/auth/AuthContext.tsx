import { useCallback, useEffect, useRef, useState, type ReactNode } from "react";

import { isTransientError } from "@/lib/api/ApiError";
import { loginUser, logoutUser, registerUser } from "@/lib/api/endpoints/auth";
import { getCurrentUser } from "@/lib/api/endpoints/users";
import { TOKEN_STORAGE_KEY, UNAUTHORIZED_EVENT } from "@/lib/api/client";
import type { UserResponse } from "@/lib/api/types";
import { AuthContext, type AuthStatus } from "@/features/auth/authContextDefinition";

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserResponse | null>(null);
  const [status, setStatus] = useState<AuthStatus>("loading");
  const bootstrapped = useRef(false);

  const checkSession = useCallback(() => {
    setStatus("loading");

    // getCurrentUser() goes through apiFetch, which already retries once via a silent
    // POST /auth/refresh on a 401 — that single call is enough to cover every "still logged in"
    // case on startup: a live access token, an expired-but-refreshable one, and even no access
    // token at all in localStorage as long as the HttpOnly refresh cookie is still valid (e.g.
    // after closing and reopening the browser). Only a genuinely dead/missing/revoked refresh
    // token should show the login screen.
    getCurrentUser()
      .then((fetchedUser) => {
        setUser(fetchedUser);
        setStatus("authenticated");
      })
      .catch((err: unknown) => {
        if (isTransientError(err)) {
          // Either fetch() itself failed (offline, DNS, connection refused — status 0), or the
          // request reached a server but got a genuine infra failure back (502/503/504, most
          // notably Render's edge proxy answering while a sleeping instance cold-starts — a real
          // HTTP response, not a thrown error, so it must be classified here too). Neither is
          // evidence the user is logged out: the existing access token is left in place and the
          // UI shows a retry state instead of bouncing to /login.
          setStatus("offline");
          return;
        }
        setUser(null);
        setStatus("guest");
      });
  }, []);

  useEffect(() => {
    if (bootstrapped.current) return;
    bootstrapped.current = true;
    checkSession();
  }, [checkSession]);

  useEffect(() => {
    function handleUnauthorized() {
      setUser(null);
      setStatus("guest");
    }
    window.addEventListener(UNAUTHORIZED_EVENT, handleUnauthorized);
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, handleUnauthorized);
  }, []);

  async function login(email: string, password: string) {
    const auth = await loginUser({ email, password });
    localStorage.setItem(TOKEN_STORAGE_KEY, auth.accessToken);
    const fullUser = await getCurrentUser();
    setUser(fullUser);
    setStatus("authenticated");
  }

  async function register(fullName: string, email: string, password: string) {
    const auth = await registerUser({ fullName, email, password });
    localStorage.setItem(TOKEN_STORAGE_KEY, auth.accessToken);
    const fullUser = await getCurrentUser();
    setUser(fullUser);
    setStatus("authenticated");
  }

  function logout() {
    // Best-effort: the refresh token is revoked server-side in the background, but the local
    // session ends immediately regardless of whether that request succeeds — a network hiccup
    // must not be able to trap the user in a "still logged in" state after they asked to leave.
    logoutUser().catch(() => {});
    localStorage.removeItem(TOKEN_STORAGE_KEY);
    setUser(null);
    setStatus("guest");
  }

  return (
    <AuthContext.Provider value={{ user, status, login, register, logout, setUser, retryAuthCheck: checkSession }}>
      {children}
    </AuthContext.Provider>
  );
}

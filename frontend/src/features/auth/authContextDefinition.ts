import { createContext } from "react";

import type { UserResponse } from "@/lib/api/types";

// "offline" is distinct from "guest": it means the backend could not be reached at all (network
// failure, Render cold start, etc), so nothing was actually learned about whether the user is
// logged in. Route guards must not treat it as "logged out" — see ProtectedRoute.
export type AuthStatus = "loading" | "authenticated" | "guest" | "offline";

export interface AuthContextValue {
  user: UserResponse | null;
  status: AuthStatus;
  login: (email: string, password: string) => Promise<void>;
  register: (fullName: string, email: string, password: string) => Promise<void>;
  logout: () => void;
  setUser: (user: UserResponse) => void;
  /** Re-runs the session check after an "offline" result — used by the retry action shown when
   * the backend couldn't be reached at all. */
  retryAuthCheck: () => void;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

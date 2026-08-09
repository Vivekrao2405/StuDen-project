import { createContext } from "react";

import type { UserResponse } from "@/lib/api/types";

export type AuthStatus = "loading" | "authenticated" | "guest";

export interface AuthContextValue {
  user: UserResponse | null;
  status: AuthStatus;
  login: (email: string, password: string) => Promise<void>;
  register: (fullName: string, email: string, password: string) => Promise<void>;
  logout: () => void;
  setUser: (user: UserResponse) => void;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

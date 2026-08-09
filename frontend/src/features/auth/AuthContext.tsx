import { useEffect, useRef, useState, type ReactNode } from "react";

import { loginUser, registerUser } from "@/lib/api/endpoints/auth";
import { getCurrentUser } from "@/lib/api/endpoints/users";
import { TOKEN_STORAGE_KEY, UNAUTHORIZED_EVENT } from "@/lib/api/client";
import type { UserResponse } from "@/lib/api/types";
import { AuthContext, type AuthStatus } from "@/features/auth/authContextDefinition";

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserResponse | null>(null);
  const [status, setStatus] = useState<AuthStatus>("loading");
  const bootstrapped = useRef(false);

  useEffect(() => {
    if (bootstrapped.current) return;
    bootstrapped.current = true;

    const token = localStorage.getItem(TOKEN_STORAGE_KEY);
    if (!token) {
      setStatus("guest");
      return;
    }

    getCurrentUser()
      .then((fetchedUser) => {
        setUser(fetchedUser);
        setStatus("authenticated");
      })
      .catch(() => {
        localStorage.removeItem(TOKEN_STORAGE_KEY);
        setUser(null);
        setStatus("guest");
      });
  }, []);

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
    localStorage.removeItem(TOKEN_STORAGE_KEY);
    setUser(null);
    setStatus("guest");
  }

  return (
    <AuthContext.Provider value={{ user, status, login, register, logout, setUser }}>
      {children}
    </AuthContext.Provider>
  );
}

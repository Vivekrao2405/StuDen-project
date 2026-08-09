import { useContext } from "react";

import { AuthContext } from "@/features/auth/authContextDefinition";

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return ctx;
}

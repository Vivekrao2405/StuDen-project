import { Navigate, Outlet } from "react-router-dom";

import { LoadingState } from "@/components/shared/LoadingState";
import { useAuth } from "@/features/auth/useAuth";
import { ROUTES } from "@/lib/routes";

export function GuestOnlyRoute() {
  const { status } = useAuth();

  if (status === "loading") {
    return <LoadingState label="Loading..." />;
  }

  if (status === "authenticated") {
    return <Navigate to={ROUTES.dashboard} replace />;
  }

  return <Outlet />;
}

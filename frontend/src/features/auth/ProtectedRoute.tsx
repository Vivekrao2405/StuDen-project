import { Navigate, Outlet, useLocation } from "react-router-dom";

import { ErrorState } from "@/components/shared/ErrorState";
import { LoadingState } from "@/components/shared/LoadingState";
import { useAuth } from "@/features/auth/useAuth";
import { ROUTES } from "@/lib/routes";

export function ProtectedRoute() {
  const { status, retryAuthCheck } = useAuth();
  const location = useLocation();

  if (status === "loading") {
    return <LoadingState label="Checking your session..." />;
  }

  // The backend couldn't be reached at all — this says nothing about whether the user is
  // logged in, so it must never redirect to /login (that would look exactly like an
  // unwanted logout). Keep them on the page and offer a retry once the backend is reachable
  // again.
  if (status === "offline") {
    return (
      <ErrorState
        title="StuDen is temporarily unavailable"
        message="We couldn't reach the server. Please check your connection and try again."
        onRetry={retryAuthCheck}
      />
    );
  }

  if (status === "guest") {
    return <Navigate to={ROUTES.login} replace state={{ from: location }} />;
  }

  return <Outlet />;
}

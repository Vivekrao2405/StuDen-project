import { Navigate, Outlet } from "react-router-dom";

import { ErrorState } from "@/components/shared/ErrorState";
import { LoadingState } from "@/components/shared/LoadingState";
import { useAuth } from "@/features/auth/useAuth";
import { ROUTES } from "@/lib/routes";

// Layered on top of ProtectedRoute (nested inside it in App.tsx), so `status` is never "guest"
// here in practice — this only adds the ADMIN role check. The backend enforces this
// independently via @PreAuthorize on every admin endpoint (see AdminQuestionController); this
// guard exists purely so a non-admin never sees the management UI at all, not as the real
// security boundary.
export function AdminRoute() {
  const { status, user, retryAuthCheck } = useAuth();

  if (status === "loading") {
    return <LoadingState label="Checking your session..." />;
  }

  if (status === "offline") {
    return (
      <ErrorState
        title="StuDen is temporarily unavailable"
        message="We couldn't reach the server. Please check your connection and try again."
        onRetry={retryAuthCheck}
      />
    );
  }

  if (status === "guest" || user?.role !== "ADMIN") {
    return <Navigate to={ROUTES.dashboard} replace />;
  }

  return <Outlet />;
}

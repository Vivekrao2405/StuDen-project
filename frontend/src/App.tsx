import { Route, Routes } from "react-router-dom";

import { AppLayout } from "@/components/layout/AppLayout";
import { AuthLayout } from "@/components/layout/AuthLayout";
import { MarketingLayout } from "@/components/layout/MarketingLayout";
import { PublicLayout } from "@/components/layout/PublicLayout";
import { GuestOnlyRoute } from "@/features/auth/GuestOnlyRoute";
import { ProtectedRoute } from "@/features/auth/ProtectedRoute";
import { HomePage } from "@/pages/HomePage";
import { LoginPage } from "@/pages/LoginPage";
import { NotFoundPage } from "@/pages/NotFoundPage";
import { PortfolioDashboardPage } from "@/pages/PortfolioDashboardPage";
import { PublicProfilePage } from "@/pages/PublicProfilePage";
import { RegisterPage } from "@/pages/RegisterPage";
import { SettingsPage } from "@/pages/SettingsPage";
import { ShareProfilePage } from "@/pages/ShareProfilePage";

function App() {
  return (
    <Routes>
      <Route element={<MarketingLayout />}>
        <Route path="/" element={<HomePage />} />
      </Route>

      <Route element={<AuthLayout />}>
        <Route element={<GuestOnlyRoute />}>
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/login" element={<LoginPage />} />
        </Route>
      </Route>

      <Route element={<AppLayout />}>
        <Route element={<ProtectedRoute />}>
          <Route path="/profile" element={<PortfolioDashboardPage />} />
          <Route path="/profile/share" element={<ShareProfilePage />} />
          <Route path="/settings" element={<SettingsPage />} />
        </Route>
      </Route>

      <Route element={<PublicLayout />}>
        <Route path="/u/:slug" element={<PublicProfilePage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}

export default App;

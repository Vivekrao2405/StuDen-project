import { Trophy } from "lucide-react";
import { Route, Routes } from "react-router-dom";

import { AppLayout } from "@/components/layout/AppLayout";
import { AuthLayout } from "@/components/layout/AuthLayout";
import { MarketingLayout } from "@/components/layout/MarketingLayout";
import { PublicLayout } from "@/components/layout/PublicLayout";
import { AdminRoute } from "@/features/auth/AdminRoute";
import { GuestOnlyRoute } from "@/features/auth/GuestOnlyRoute";
import { ProtectedRoute } from "@/features/auth/ProtectedRoute";
import { QuestionBankPage } from "@/pages/admin/QuestionBankPage";
import { QuestionEditorPage } from "@/pages/admin/QuestionEditorPage";
import { AssessmentInstructionsPage } from "@/pages/AssessmentInstructionsPage";
import { AssessmentResultPage } from "@/pages/AssessmentResultPage";
import { AssessmentTakingPage } from "@/pages/AssessmentTakingPage";
import { ComingSoonPage } from "@/pages/ComingSoonPage";
import { CreateServicePage } from "@/pages/CreateServicePage";
import { DashboardPage } from "@/pages/DashboardPage";
import { HomePage } from "@/pages/HomePage";
import { LoginPage } from "@/pages/LoginPage";
import { MarketplacePage } from "@/pages/MarketplacePage";
import { MessagesPage } from "@/pages/MessagesPage";
import { NotFoundPage } from "@/pages/NotFoundPage";
import { NotificationsPage } from "@/pages/NotificationsPage";
import { OrderDetailPage } from "@/pages/OrderDetailPage";
import { OrdersPage } from "@/pages/OrdersPage";
import { PortfolioDashboardPage } from "@/pages/PortfolioDashboardPage";
import { PublicProfilePage } from "@/pages/PublicProfilePage";
import { PublicProjectDetailPage } from "@/pages/PublicProjectDetailPage";
import { RegisterPage } from "@/pages/RegisterPage";
import { ServiceDetailPage } from "@/pages/ServiceDetailPage";
import { ServiceRequestDetailPage } from "@/pages/ServiceRequestDetailPage";
import { ServiceRequestsPage } from "@/pages/ServiceRequestsPage";
import { SettingsPage } from "@/pages/SettingsPage";
import { ShareProfilePage } from "@/pages/ShareProfilePage";
import { ShowcasePage } from "@/pages/ShowcasePage";
import { SkillAssessmentsPage } from "@/pages/SkillAssessmentsPage";
import { ROUTES } from "@/lib/routes";

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
          <Route path={ROUTES.dashboard} element={<DashboardPage />} />
          <Route path="/profile" element={<PortfolioDashboardPage />} />
          <Route path="/profile/share" element={<ShareProfilePage />} />
          <Route path={ROUTES.settings} element={<SettingsPage />} />
          <Route path={ROUTES.marketplace} element={<MarketplacePage />} />
          <Route path={ROUTES.createService} element={<CreateServicePage />} />
          <Route path={ROUTES.serviceRequests} element={<ServiceRequestsPage />} />
          <Route path="/requests/:requestId" element={<ServiceRequestDetailPage />} />
          <Route path={ROUTES.orders} element={<OrdersPage />} />
          <Route path="/orders/:orderId" element={<OrderDetailPage />} />
          <Route path={ROUTES.skillAssessments} element={<SkillAssessmentsPage />} />
          <Route path="/skill-assessments/:skillId" element={<AssessmentInstructionsPage />} />
          <Route path="/assessments/:assessmentId" element={<AssessmentTakingPage />} />
          <Route path="/assessments/:assessmentId/result" element={<AssessmentResultPage />} />
          <Route
            path={ROUTES.challenges}
            element={
              <ComingSoonPage
                title="Challenges"
                description="Win rewards and build skills by joining challenges once they launch."
                icon={Trophy}
              />
            }
          />
          <Route path={ROUTES.messages} element={<MessagesPage />} />
          <Route path="/messages/:conversationId" element={<MessagesPage />} />
          <Route path={ROUTES.notifications} element={<NotificationsPage />} />
          <Route path={ROUTES.projects} element={<ShowcasePage />} />

          <Route element={<AdminRoute />}>
            <Route path={ROUTES.questionBank} element={<QuestionBankPage />} />
            <Route path={ROUTES.createQuestion} element={<QuestionEditorPage />} />
            <Route path="/admin/question-bank/:questionId" element={<QuestionEditorPage />} />
          </Route>
        </Route>
      </Route>

      <Route element={<PublicLayout />}>
        {/* Publicly viewable even when logged out — the backing GET /public/services/{id} call
            is unauthenticated, and a shared service link must open without requiring login. */}
        <Route path="/marketplace/services/:serviceId" element={<ServiceDetailPage />} />
        <Route path="/u/:slug" element={<PublicProfilePage />} />
        <Route path="/u/:slug/projects/:projectId" element={<PublicProjectDetailPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}

export default App;

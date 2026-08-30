import { Trophy } from "lucide-react";
import { Navigate, Route, Routes } from "react-router-dom";

import { AppLayout } from "@/components/layout/AppLayout";
import { AuthLayout } from "@/components/layout/AuthLayout";
import { MarketingLayout } from "@/components/layout/MarketingLayout";
import { PublicLayout } from "@/components/layout/PublicLayout";
import { AdminRoute } from "@/features/auth/AdminRoute";
import { GuestOnlyRoute } from "@/features/auth/GuestOnlyRoute";
import { ProtectedRoute } from "@/features/auth/ProtectedRoute";
import { QuestionBankPage } from "@/pages/admin/QuestionBankPage";
import { QuestionEditorPage } from "@/pages/admin/QuestionEditorPage";
import { QuestionImportPage } from "@/pages/admin/QuestionImportPage";
import { PracticalAssessmentEditorPage } from "@/pages/admin/practical/PracticalAssessmentEditorPage";
import { PracticalAssessmentsAdminPage } from "@/pages/admin/practical/PracticalAssessmentsAdminPage";
import { PracticalAttemptEvaluatePage } from "@/pages/admin/practical/PracticalAttemptEvaluatePage";
import { PracticalAttemptQueuePage } from "@/pages/admin/practical/PracticalAttemptQueuePage";
import { CampaignAnalyticsPage } from "@/pages/admin/communications/CampaignAnalyticsPage";
import { CampaignWizardPage } from "@/pages/admin/communications/CampaignWizardPage";
import { CampaignsPage } from "@/pages/admin/communications/CampaignsPage";
import { SegmentsPage } from "@/pages/admin/communications/SegmentsPage";
import { TemplatesPage } from "@/pages/admin/communications/TemplatesPage";
import { ResourceEditorPage } from "@/pages/admin/resources/ResourceEditorPage";
import { ResourcesAdminPage } from "@/pages/admin/resources/ResourcesAdminPage";
import { UserManagementPage } from "@/pages/admin/UserManagementPage";
import { MyLearningPage } from "@/pages/learning/MyLearningPage";
import { ResourceDetailPage } from "@/pages/learning/ResourceDetailPage";
import { PracticalAssessmentDetailPage } from "@/pages/practical/PracticalAssessmentDetailPage";
import { PracticalAttemptPage } from "@/pages/practical/PracticalAttemptPage";
import { PracticalAttemptResultPage } from "@/pages/practical/PracticalAttemptResultPage";
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
          <Route path={ROUTES.myLearning} element={<MyLearningPage />} />
          <Route path="/my-learning/resources/:id" element={<ResourceDetailPage />} />
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
          <Route path={ROUTES.practicalAssessments} element={<Navigate to={ROUTES.skillAssessments} replace />} />
          <Route path="/practical-assessments/:id" element={<PracticalAssessmentDetailPage />} />
          <Route path="/practical-attempts/:id" element={<PracticalAttemptPage />} />
          <Route path="/practical-attempts/:id/result" element={<PracticalAttemptResultPage />} />

          <Route element={<AdminRoute />}>
            <Route path={ROUTES.questionBank} element={<QuestionBankPage />} />
            <Route path={ROUTES.createQuestion} element={<QuestionEditorPage />} />
            <Route path={ROUTES.importQuestions} element={<QuestionImportPage />} />
            <Route path="/admin/question-bank/:questionId" element={<QuestionEditorPage />} />
            <Route path={ROUTES.userManagement} element={<UserManagementPage />} />
            <Route path={ROUTES.adminPracticalAssessments} element={<PracticalAssessmentsAdminPage />} />
            <Route path={ROUTES.adminCreatePracticalAssessment} element={<PracticalAssessmentEditorPage />} />
            <Route path="/admin/practical-assessments/:id" element={<PracticalAssessmentEditorPage />} />
            <Route path={ROUTES.adminPracticalAttempts} element={<PracticalAttemptQueuePage />} />
            <Route path="/admin/practical-attempts/:id" element={<PracticalAttemptEvaluatePage />} />
            <Route path={ROUTES.adminResources} element={<ResourcesAdminPage />} />
            <Route path={ROUTES.adminCreateResource} element={<ResourceEditorPage />} />
            <Route path="/admin/resources/:id" element={<ResourceEditorPage />} />
            <Route path={ROUTES.adminCommunications} element={<CampaignsPage />} />
            <Route path={ROUTES.adminCommunicationsNew} element={<CampaignWizardPage />} />
            <Route path="/admin/communications/campaigns/:id" element={<CampaignWizardPage />} />
            <Route path="/admin/communications/campaigns/:id/analytics" element={<CampaignAnalyticsPage />} />
            <Route path={ROUTES.adminCommunicationsTemplates} element={<TemplatesPage />} />
            <Route path={ROUTES.adminCommunicationsSegments} element={<SegmentsPage />} />
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

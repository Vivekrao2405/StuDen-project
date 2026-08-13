import { useEffect, useState } from "react";

import { ErrorState } from "@/components/shared/ErrorState";
import { LoadingState } from "@/components/shared/LoadingState";
import { useAuth } from "@/features/auth/useAuth";
import { useToast } from "@/hooks/useToast";
import { ApiError } from "@/lib/api/ApiError";
import { listCertificates } from "@/lib/api/endpoints/certificates";
import { listEducation } from "@/lib/api/endpoints/education";
import { getMyPortfolio, updateSkillLevel } from "@/lib/api/endpoints/portfolio";
import { listProjects } from "@/lib/api/endpoints/projects";
import type {
  CertificateResponse,
  EducationResponse,
  PortfolioResponse,
  ProjectResponse,
  SkillLevel,
} from "@/lib/api/types";
import { useAsync } from "@/lib/hooks/useAsync";
import { computeProfileCompletion } from "@/lib/profileCompletion";
import { ActiveOrdersCard } from "@/pages/dashboard/ActiveOrdersCard";
import { ContinueYourJourney } from "@/pages/dashboard/ContinueYourJourney";
import { DashboardHeader } from "@/pages/dashboard/DashboardHeader";
import { MobileQuickLinks } from "@/pages/dashboard/MobileQuickLinks";
import { OpportunitiesForYou } from "@/pages/dashboard/OpportunitiesForYou";
import { ProfileSummaryCard } from "@/pages/dashboard/ProfileSummaryCard";
import { RecentMessages } from "@/pages/dashboard/RecentMessages";
import { ServiceRequestsCard } from "@/pages/dashboard/ServiceRequestsCard";
import { UpcomingChallengesCard } from "@/pages/dashboard/UpcomingChallengesCard";
import { YourProjectsCard } from "@/pages/dashboard/YourProjectsCard";
import { YourSkillsCard } from "@/pages/dashboard/YourSkillsCard";

interface DashboardData {
  portfolio: PortfolioResponse;
  education: EducationResponse[];
  certificates: CertificateResponse[];
  projects: ProjectResponse[];
}

async function loadPortfolioData(): Promise<DashboardData> {
  const [portfolio, education, certificates, projects] = await Promise.all([
    getMyPortfolio(),
    listEducation(),
    listCertificates(),
    listProjects(),
  ]);
  return { portfolio, education, certificates, projects };
}

export function DashboardPage() {
  const { user } = useAuth();
  const toast = useToast();
  const { data, error, loading, refetch } = useAsync(loadPortfolioData, []);
  const [portfolio, setPortfolio] = useState<PortfolioResponse | null>(null);

  useEffect(() => {
    if (data) setPortfolio(data.portfolio);
  }, [data]);

  // getMyPortfolio/listEducation/listCertificates all throw the identical "Student portfolio
  // not found" 404 for a brand-new user with no portfolio yet — that's a valid empty state, not
  // an error banner. Any other error is real.
  const noPortfolio = error instanceof ApiError && error.message.includes("portfolio");

  if (loading || !user) {
    return <LoadingState label="Loading your dashboard..." />;
  }

  if (error && !noPortfolio) {
    return <ErrorState message={error.message} onRetry={refetch} />;
  }

  const education = data?.education ?? [];
  const certificates = data?.certificates ?? [];
  const projects = data?.projects ?? [];
  const completion = computeProfileCompletion(user, portfolio, education, certificates, projects);

  async function handleLevelChange(skillId: string, level: SkillLevel) {
    try {
      const updated = await updateSkillLevel(skillId, level);
      setPortfolio(updated);
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Couldn't update that skill's level.");
    }
  }

  return (
    <div className="space-y-6">
      <DashboardHeader fullName={user.fullName} />

      <ProfileSummaryCard user={user} portfolio={portfolio} completion={completion} />

      <ContinueYourJourney hasEducation={education.length > 0} hasProjects={projects.length > 0} />

      <div className="lg:hidden">
        <MobileQuickLinks skillsCount={portfolio?.skills.length ?? 0} projectsCount={projects.length} />
      </div>

      <div className="hidden space-y-6 lg:block">
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
          <YourSkillsCard skills={portfolio?.skills ?? []} onLevelChange={handleLevelChange} />
          <YourProjectsCard projects={projects} />
          <UpcomingChallengesCard />
        </div>
        <OpportunitiesForYou />
        <div className="grid grid-cols-1 gap-4 md:grid-cols-2 xl:grid-cols-3">
          <ServiceRequestsCard />
          <ActiveOrdersCard />
          <RecentMessages />
        </div>
      </div>
    </div>
  );
}

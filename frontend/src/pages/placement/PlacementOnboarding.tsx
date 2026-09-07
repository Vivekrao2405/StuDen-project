import { Briefcase } from "lucide-react";
import { useEffect, useRef, useState } from "react";

import { EmptyState } from "@/components/shared/EmptyState";
import { ErrorState } from "@/components/shared/ErrorState";
import { SelectableOption } from "@/components/shared/SelectableOption";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useToast } from "@/hooks/useToast";
import { ApiError } from "@/lib/api/ApiError";
import { listPlacementRoleSkills, listPlacementRoles, saveMyPlacementProfile } from "@/lib/api/endpoints/placement";
import type {
  CompanyType,
  ExperienceLevel,
  PlacementCompanyResponse,
  PlacementProfileRequest,
  PlacementProfileResponse,
} from "@/lib/api/placementTypes";
import { useAsync } from "@/lib/hooks/useAsync";
import { CurrentSkillsStep } from "@/pages/placement/CurrentSkillsStep";
import { COMPANY_TYPE_OPTIONS, EXPERIENCE_LEVEL_OPTIONS } from "@/pages/placement/placementDisplay";
import { PlacementReviewStep } from "@/pages/placement/PlacementReviewStep";
import { TargetCompaniesStep } from "@/pages/placement/TargetCompaniesStep";

const STEP_TITLES = ["Target Role", "Company Type", "Target Companies", "Current Skills", "Experience Level"];
const STEP_QUESTIONS = [
  "What role are you targeting?",
  "What type of companies are you targeting?",
  "Which companies are you targeting?",
  "Which of these skills do you currently have?",
  "What is your current experience level?",
];

interface PlacementOnboardingProps {
  existingProfile: PlacementProfileResponse | null;
  onSaved: (profile: PlacementProfileResponse) => void;
  onCancel?: () => void;
}

export function PlacementOnboarding({ existingProfile, onSaved, onCancel }: PlacementOnboardingProps) {
  const toast = useToast();
  const isEditing = existingProfile !== null;

  const [step, setStep] = useState(isEditing ? 6 : 1);
  const [targetRoleId, setTargetRoleId] = useState<string | null>(existingProfile?.targetRoleId ?? null);
  const [companyTypes, setCompanyTypes] = useState<CompanyType[]>(existingProfile?.companyTypes ?? []);
  const [selectedCompanies, setSelectedCompanies] = useState<PlacementCompanyResponse[]>(
    existingProfile?.targetCompanies ?? []
  );
  const [manualCompanies, setManualCompanies] = useState<string[]>(existingProfile?.manualTargetCompanies ?? []);
  const [currentSkillIds, setCurrentSkillIds] = useState<string[]>(
    existingProfile?.currentSkills.map((s) => s.id) ?? []
  );
  const [experienceLevel, setExperienceLevel] = useState<ExperienceLevel | null>(
    existingProfile?.experienceLevel ?? null
  );
  const [submitting, setSubmitting] = useState(false);

  const roles = useAsync(() => listPlacementRoles(), []);
  const roleSkills = useAsync(
    () => (targetRoleId ? listPlacementRoleSkills(targetRoleId) : Promise.resolve([])),
    [targetRoleId]
  );

  const previousRoleId = useRef(targetRoleId);
  useEffect(() => {
    if (!roleSkills.data) return;
    const validIds = new Set(roleSkills.data.map((rs) => rs.skillId));
    setCurrentSkillIds((prev) => {
      const stale = prev.filter((id) => !validIds.has(id));
      if (stale.length === 0) return prev;
      if (previousRoleId.current !== targetRoleId) {
        toast.error(
          `${stale.length} previously selected skill${stale.length === 1 ? "" : "s"} ${
            stale.length === 1 ? "isn't" : "aren't"
          } part of this role and ${stale.length === 1 ? "was" : "were"} removed.`
        );
      }
      return prev.filter((id) => validIds.has(id));
    });
    previousRoleId.current = targetRoleId;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [roleSkills.data, targetRoleId]);

  function toggleCompanyType(type: CompanyType) {
    setCompanyTypes((prev) => (prev.includes(type) ? prev.filter((t) => t !== type) : [...prev, type]));
  }

  function canGoNext(): boolean {
    if (step === 1) return targetRoleId !== null;
    if (step === 5) return experienceLevel !== null;
    return true;
  }

  function goNext() {
    if (!canGoNext()) return;
    setStep((s) => Math.min(6, s + 1));
  }

  function goBack() {
    setStep((s) => Math.max(1, s - 1));
  }

  async function handleSubmit() {
    if (!targetRoleId || !experienceLevel) {
      toast.error("Please complete all required steps.");
      return;
    }
    setSubmitting(true);
    try {
      const payload: PlacementProfileRequest = {
        targetRoleId,
        experienceLevel,
        companyTypes,
        targetCompanyIds: selectedCompanies.map((c) => c.id),
        manualTargetCompanies: manualCompanies,
        currentSkillIds,
      };
      const saved = await saveMyPlacementProfile(payload);
      toast.success(isEditing ? "Placement profile updated." : "Placement profile created.");
      onSaved(saved);
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  const selectedRole = roles.data?.find((r) => r.id === targetRoleId) ?? null;
  const currentSkillNames = (roleSkills.data ?? [])
    .filter((rs) => currentSkillIds.includes(rs.skillId))
    .map((rs) => rs.skillName);

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h1 className="text-2xl font-bold text-foreground">
            {isEditing ? "Edit Placement Profile" : "Set Up Your Placement Profile"}
          </h1>
          <p className="text-sm text-muted-foreground">
            {isEditing
              ? "Update your target role, companies, skills, and experience level."
              : "Tell us what you're aiming for so we can personalize your placement prep."}
          </p>
        </div>
        {onCancel ? (
          <Button variant="ghost" size="sm" onClick={onCancel}>
            Cancel
          </Button>
        ) : null}
      </div>

      {step <= 5 ? (
        <div className="space-y-2">
          <p className="text-xs font-medium text-muted-foreground">Step {step} of 5</p>
          <div className="h-1.5 w-full overflow-hidden rounded-full bg-muted">
            <div
              className="h-full rounded-full bg-primary transition-all"
              style={{ width: `${(step / 5) * 100}%` }}
            />
          </div>
          <h2 className="text-lg font-semibold text-foreground">{STEP_TITLES[step - 1]}</h2>
          <p className="text-sm text-muted-foreground">{STEP_QUESTIONS[step - 1]}</p>
        </div>
      ) : (
        <div className="space-y-1">
          <h2 className="text-lg font-semibold text-foreground">Review Your Placement Profile</h2>
          <p className="text-sm text-muted-foreground">Confirm your selections, or edit any section below.</p>
        </div>
      )}

      <div>
        {step === 1 ? (
          roles.loading ? (
            <div className="space-y-2" aria-hidden="true">
              {Array.from({ length: 4 }).map((_, i) => (
                <Skeleton key={i} className="h-14 w-full rounded-xl" />
              ))}
            </div>
          ) : roles.error ? (
            <ErrorState message={roles.error.message} onRetry={roles.refetch} />
          ) : !roles.data || roles.data.length === 0 ? (
            <EmptyState
              icon={Briefcase}
              title="No roles available yet"
              description="Placement roles haven't been configured yet. Please check back later."
            />
          ) : (
            <div className="space-y-2" role="radiogroup" aria-label="Target role">
              {roles.data.map((role) => (
                <SelectableOption
                  key={role.id}
                  selected={targetRoleId === role.id}
                  onClick={() => setTargetRoleId(role.id)}
                  title={role.name}
                  subtitle={role.description ?? undefined}
                />
              ))}
            </div>
          )
        ) : null}

        {step === 2 ? (
          <div className="space-y-2" role="group" aria-label="Company types">
            {COMPANY_TYPE_OPTIONS.map(([value, label]) => (
              <SelectableOption
                key={value}
                multi
                selected={companyTypes.includes(value)}
                onClick={() => toggleCompanyType(value)}
                title={label}
              />
            ))}
          </div>
        ) : null}

        {step === 3 ? (
          <TargetCompaniesStep
            selectedCompanies={selectedCompanies}
            onChangeCompanies={setSelectedCompanies}
            manualCompanies={manualCompanies}
            onChangeManual={setManualCompanies}
          />
        ) : null}

        {step === 4 ? (
          <CurrentSkillsStep
            roleSkills={roleSkills.data}
            loading={roleSkills.loading}
            error={roleSkills.error?.message ?? null}
            onRetry={roleSkills.refetch}
            selectedSkillIds={currentSkillIds}
            onChange={setCurrentSkillIds}
          />
        ) : null}

        {step === 5 ? (
          <div className="space-y-2" role="radiogroup" aria-label="Experience level">
            {EXPERIENCE_LEVEL_OPTIONS.map(([value, label]) => (
              <SelectableOption
                key={value}
                selected={experienceLevel === value}
                onClick={() => setExperienceLevel(value)}
                title={label}
              />
            ))}
          </div>
        ) : null}

        {step === 6 ? (
          <PlacementReviewStep
            roleName={selectedRole?.name ?? null}
            companyTypes={companyTypes}
            selectedCompanies={selectedCompanies}
            manualCompanies={manualCompanies}
            currentSkillNames={currentSkillNames}
            experienceLevel={experienceLevel}
            onEditStep={setStep}
          />
        ) : null}
      </div>

      <div className="flex items-center justify-between gap-3 pt-2">
        <Button variant="outline" onClick={goBack} disabled={step === 1 || submitting}>
          Back
        </Button>
        {step < 6 ? (
          <Button onClick={goNext} disabled={!canGoNext() || submitting}>
            Next
          </Button>
        ) : (
          <Button onClick={handleSubmit} disabled={submitting}>
            {submitting ? "Saving..." : isEditing ? "Save Changes" : "Complete Placement Profile"}
          </Button>
        )}
      </div>
    </div>
  );
}

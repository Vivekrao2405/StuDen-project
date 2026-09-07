import { useState } from "react";

import { ErrorState } from "@/components/shared/ErrorState";
import { LoadingState } from "@/components/shared/LoadingState";
import { getMyPlacementProfile } from "@/lib/api/endpoints/placement";
import { useAsync } from "@/lib/hooks/useAsync";
import { PlacementOnboarding } from "@/pages/placement/PlacementOnboarding";
import { PlacementProfileOverview } from "@/pages/placement/PlacementProfileOverview";

/** Entry point for "Placement" in the nav. A first-time student (no PlacementProfile yet, i.e.
 * the GET resolves to undefined for the 204 response) is guided through onboarding; a returning
 * student sees their existing profile instead of being forced through it again. */
export function PlacementPage() {
  const profile = useAsync(() => getMyPlacementProfile(), []);
  const [editing, setEditing] = useState(false);

  if (profile.loading) {
    return <LoadingState label="Loading your placement profile..." />;
  }
  if (profile.error) {
    return <ErrorState message={profile.error.message} onRetry={profile.refetch} />;
  }

  if (!profile.data || editing) {
    return (
      <PlacementOnboarding
        existingProfile={profile.data ?? null}
        onSaved={() => {
          setEditing(false);
          profile.refetch();
        }}
        onCancel={profile.data ? () => setEditing(false) : undefined}
      />
    );
  }

  return <PlacementProfileOverview profile={profile.data} onEdit={() => setEditing(true)} />;
}

import { useNavigate } from "react-router-dom";

import { useToast } from "@/hooks/useToast";
import type { ServiceResponse } from "@/lib/api/types";
import { ROUTES } from "@/lib/routes";
import { ServiceForm } from "@/pages/marketplace/ServiceForm";

export function CreateServicePage() {
  const navigate = useNavigate();
  const toast = useToast();

  function handleSaved(service: ServiceResponse) {
    toast.success(service.status === "DRAFT" ? "Draft saved." : "Service updated.");
    navigate(ROUTES.profile);
  }

  function handlePublished(service: ServiceResponse) {
    navigate(ROUTES.serviceDetail(service.id), { state: { justPublished: true } });
  }

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-foreground">Create a Service</h1>
        <p className="text-sm text-muted-foreground">Turn your skills into something other students can discover.</p>
      </div>

      <ServiceForm onSaved={handleSaved} onPublished={handlePublished} onCancel={() => navigate(ROUTES.marketplace)} />
    </div>
  );
}

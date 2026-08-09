import { Outlet } from "react-router-dom";

import { MarketingFooter } from "@/components/layout/MarketingFooter";
import { MarketingNavbar } from "@/components/layout/MarketingNavbar";

export function MarketingLayout() {
  return (
    <div className="flex min-h-svh flex-col bg-background">
      <MarketingNavbar />
      <main className="flex-1">
        <Outlet />
      </main>
      <MarketingFooter />
    </div>
  );
}

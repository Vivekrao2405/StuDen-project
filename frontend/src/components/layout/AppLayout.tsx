import { Outlet } from "react-router-dom";

import { AppNavbar } from "@/components/layout/AppNavbar";

export function AppLayout() {
  return (
    <div className="flex min-h-svh flex-col bg-background">
      <AppNavbar />
      <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-8 sm:px-6 lg:px-8">
        <Outlet />
      </main>
    </div>
  );
}

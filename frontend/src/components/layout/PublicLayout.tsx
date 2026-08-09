import { Link, Outlet } from "react-router-dom";

import { Button } from "@/components/ui/button";
import { Logo } from "@/components/layout/Logo";
import { UserMenu } from "@/components/layout/UserMenu";
import { useAuth } from "@/features/auth/useAuth";
import { ROUTES } from "@/lib/routes";

export function PublicLayout() {
  const { status } = useAuth();

  return (
    <div className="flex min-h-svh flex-col bg-background">
      <header className="border-b border-border">
        <div className="mx-auto flex h-16 max-w-6xl items-center justify-between px-4 sm:px-6 lg:px-8">
          <Logo size="sm" />
          {status === "authenticated" ? (
            <UserMenu />
          ) : (
            <div className="flex items-center gap-3">
              <Button variant="ghost" render={<Link to={ROUTES.login} />}>
                Log in
              </Button>
              <Button variant="default" render={<Link to={ROUTES.register} />}>
                Get Started
              </Button>
            </div>
          )}
        </div>
      </header>
      <main className="flex-1">
        <Outlet />
      </main>
    </div>
  );
}

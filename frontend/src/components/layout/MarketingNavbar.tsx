import { ArrowRight, Menu, X } from "lucide-react";
import { useState } from "react";
import { Link, NavLink } from "react-router-dom";

import { Button } from "@/components/ui/button";
import { Logo } from "@/components/layout/Logo";
import { UserMenu } from "@/components/layout/UserMenu";
import { useAuth } from "@/features/auth/useAuth";
import { cn } from "@/lib/utils";
import { ROUTES } from "@/lib/routes";

// "Opportunities" has no backing route anywhere in the app yet (no jobs/postings feature) — kept
// as a non-interactive label rather than a dead or invented link. Every other item routes to a
// real page.
const NAV_LINKS = [
  { label: "Home", to: ROUTES.home },
  { label: "Placement", to: ROUTES.skillAssessments },
  { label: "Challenges", to: ROUTES.challenges },
  { label: "Opportunities", to: null },
  { label: "Marketplace", to: ROUTES.marketplace },
] as const;

export function MarketingNavbar() {
  const { status } = useAuth();
  const [mobileOpen, setMobileOpen] = useState(false);

  return (
    <header className="sticky top-0 z-40 border-b border-border bg-background/95 backdrop-blur supports-backdrop-filter:bg-background/80">
      <div className="mx-auto flex h-16 max-w-7xl items-center justify-between px-4 sm:px-6 lg:px-8">
        <Logo />

        <nav className="hidden items-center gap-7 md:flex">
          {NAV_LINKS.map((link) =>
            link.to ? (
              <NavLink
                key={link.label}
                to={link.to}
                end={link.to === ROUTES.home}
                className={({ isActive }) =>
                  cn(
                    "text-sm font-medium transition-colors hover:text-foreground",
                    isActive ? "text-primary" : "text-muted-foreground"
                  )
                }
              >
                {link.label}
              </NavLink>
            ) : (
              <span key={link.label} className="text-sm font-medium text-muted-foreground/50">
                {link.label}
              </span>
            )
          )}
        </nav>

        <div className="hidden items-center gap-3 md:flex">
          {status === "authenticated" ? (
            <>
              <Button variant="default" className="rounded-full" render={<Link to={ROUTES.profile} />}>
                Go to Dashboard
              </Button>
              <UserMenu />
            </>
          ) : (
            <Button variant="default" className="rounded-full" render={<Link to={ROUTES.login} />}>
              Login / Signup <ArrowRight className="size-4" />
            </Button>
          )}
        </div>

        <button
          type="button"
          aria-label="Toggle menu"
          aria-expanded={mobileOpen}
          className="flex size-9 items-center justify-center rounded-lg text-foreground md:hidden"
          onClick={() => setMobileOpen((open) => !open)}
        >
          {mobileOpen ? <X className="size-5" /> : <Menu className="size-5" />}
        </button>
      </div>

      {mobileOpen ? (
        <div className="border-t border-border px-4 pb-6 pt-2 md:hidden">
          <nav className="flex flex-col gap-1">
            {NAV_LINKS.map((link) =>
              link.to ? (
                <NavLink
                  key={link.label}
                  to={link.to}
                  end={link.to === ROUTES.home}
                  onClick={() => setMobileOpen(false)}
                  className={({ isActive }) =>
                    cn(
                      "rounded-md px-2 py-2.5 text-sm font-medium hover:bg-accent hover:text-accent-foreground",
                      isActive ? "text-primary" : "text-muted-foreground"
                    )
                  }
                >
                  {link.label}
                </NavLink>
              ) : (
                <span key={link.label} className="rounded-md px-2 py-2.5 text-sm font-medium text-muted-foreground/50">
                  {link.label}
                </span>
              )
            )}
          </nav>
          <div className="mt-4 flex flex-col gap-2">
            {status === "authenticated" ? (
              <Button
                variant="default"
                className="rounded-full"
                render={<Link to={ROUTES.profile} onClick={() => setMobileOpen(false)} />}
              >
                Go to Dashboard
              </Button>
            ) : (
              <Button
                variant="default"
                className="rounded-full"
                render={<Link to={ROUTES.login} onClick={() => setMobileOpen(false)} />}
              >
                Login / Signup <ArrowRight className="size-4" />
              </Button>
            )}
          </div>
        </div>
      ) : null}
    </header>
  );
}

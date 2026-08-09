import { Menu, X } from "lucide-react";
import { useState } from "react";
import { Link } from "react-router-dom";

import { Button } from "@/components/ui/button";
import { Logo } from "@/components/layout/Logo";
import { UserMenu } from "@/components/layout/UserMenu";
import { useAuth } from "@/features/auth/useAuth";
import { ROUTES } from "@/lib/routes";

const ANCHOR_LINKS = [
  { label: "How It Works", href: "#how-it-works" },
  { label: "Have skills?", href: "#cta-become-freelancer" },
];

export function MarketingNavbar() {
  const { status } = useAuth();
  const [mobileOpen, setMobileOpen] = useState(false);

  return (
    <header className="sticky top-0 z-40 border-b border-border bg-background/95 backdrop-blur supports-backdrop-filter:bg-background/80">
      <div className="mx-auto flex h-16 max-w-7xl items-center justify-between px-4 sm:px-6 lg:px-8">
        <Logo />

        <nav className="hidden items-center gap-8 md:flex">
          {ANCHOR_LINKS.map((link) => (
            <a
              key={link.href}
              href={link.href}
              className="text-sm font-medium text-muted-foreground transition-colors hover:text-foreground"
            >
              {link.label}
            </a>
          ))}
        </nav>

        <div className="hidden items-center gap-3 md:flex">
          {status === "authenticated" ? (
            <>
              <Button variant="default" render={<Link to={ROUTES.profile} />}>
                Go to Dashboard
              </Button>
              <UserMenu />
            </>
          ) : (
            <>
              <Button variant="ghost" render={<Link to={ROUTES.login} />}>
                Log in
              </Button>
              <Button variant="default" render={<Link to={ROUTES.register} />}>
                Get Started
              </Button>
            </>
          )}
        </div>

        <button
          type="button"
          aria-label="Toggle menu"
          className="flex size-9 items-center justify-center rounded-lg text-foreground md:hidden"
          onClick={() => setMobileOpen((open) => !open)}
        >
          {mobileOpen ? <X className="size-5" /> : <Menu className="size-5" />}
        </button>
      </div>

      {mobileOpen ? (
        <div className="border-t border-border px-4 pb-6 pt-2 md:hidden">
          <nav className="flex flex-col gap-1">
            {ANCHOR_LINKS.map((link) => (
              <a
                key={link.href}
                href={link.href}
                onClick={() => setMobileOpen(false)}
                className="rounded-md px-2 py-2.5 text-sm font-medium text-muted-foreground hover:bg-accent hover:text-accent-foreground"
              >
                {link.label}
              </a>
            ))}
          </nav>
          <div className="mt-4 flex flex-col gap-2">
            {status === "authenticated" ? (
              <Button
                variant="default"
                render={<Link to={ROUTES.profile} onClick={() => setMobileOpen(false)} />}
              >
                Go to Dashboard
              </Button>
            ) : (
              <>
                <Button variant="outline" render={<Link to={ROUTES.login} onClick={() => setMobileOpen(false)} />}>
                  Log in
                </Button>
                <Button
                  variant="default"
                  render={<Link to={ROUTES.register} onClick={() => setMobileOpen(false)} />}
                >
                  Get Started
                </Button>
              </>
            )}
          </div>
        </div>
      ) : null}
    </header>
  );
}

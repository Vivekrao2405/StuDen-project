import { Bell, Menu, MessageCircle } from "lucide-react";
import { useState } from "react";
import { useNavigate } from "react-router-dom";

import { Logo } from "@/components/layout/Logo";
import { MobileNavDrawer } from "@/components/layout/MobileNavDrawer";
import { UserMenu } from "@/components/layout/UserMenu";
import { ROUTES } from "@/lib/routes";

export function MobileHeader() {
  const navigate = useNavigate();
  const [drawerOpen, setDrawerOpen] = useState(false);

  return (
    <>
      <header className="sticky top-0 z-40 flex h-14 items-center justify-between border-b border-border bg-background/95 px-4 backdrop-blur supports-backdrop-filter:bg-background/80 lg:hidden">
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => setDrawerOpen(true)}
            aria-label="Open menu"
            className="rounded-full p-1.5 text-foreground hover:bg-muted"
          >
            <Menu className="size-5" />
          </button>
          <Logo size="sm" />
        </div>
        <div className="flex items-center gap-1">
          <button
            type="button"
            onClick={() => navigate(ROUTES.notifications)}
            aria-label="Notifications"
            className="rounded-full p-1.5 text-foreground hover:bg-muted"
          >
            <Bell className="size-5" />
          </button>
          <button
            type="button"
            onClick={() => navigate(ROUTES.messages)}
            aria-label="Messages"
            className="rounded-full p-1.5 text-foreground hover:bg-muted"
          >
            <MessageCircle className="size-5" />
          </button>
          <UserMenu />
        </div>
      </header>
      <MobileNavDrawer open={drawerOpen} onClose={() => setDrawerOpen(false)} />
    </>
  );
}

import { NavLink, useLocation } from "react-router-dom";

import skillSidebarArt from "@/assets/skill-sidebar-art.webp";
import { Logo } from "@/components/layout/Logo";
import { NAV_ITEMS } from "@/components/layout/navItems";
import { useAuth } from "@/features/auth/useAuth";
import { useUnreadMessages } from "@/features/messaging/useUnreadMessages";
import { ROUTES } from "@/lib/routes";
import { cn } from "@/lib/utils";

export function Sidebar() {
  const { unreadCount } = useUnreadMessages();
  const { user } = useAuth();
  const { pathname } = useLocation();
  const items = NAV_ITEMS.filter((item) => !item.adminOnly || user?.role === "ADMIN");

  return (
    <aside className="sticky top-0 hidden h-svh w-60 shrink-0 flex-col border-r border-border bg-card px-3 py-5 lg:flex">
      <div className="px-2 pb-6">
        <Logo size="sm" />
      </div>
      <nav className="flex flex-1 flex-col gap-1">
        {items.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end
            className={({ isActive }) =>
              cn(
                "flex items-center gap-3 rounded-lg px-3 py-2 text-sm font-medium transition-colors",
                isActive
                  ? "bg-accent text-primary"
                  : "text-muted-foreground hover:bg-muted hover:text-foreground"
              )
            }
          >
            <item.icon className="size-4.5 shrink-0" />
            {item.label}
            {item.label === "Messages" && unreadCount > 0 ? (
              <span className="ml-auto flex h-5 min-w-5 items-center justify-center rounded-full bg-primary px-1.5 text-[11px] font-semibold text-primary-foreground">
                {unreadCount > 99 ? "99+" : unreadCount}
              </span>
            ) : null}
          </NavLink>
        ))}
      </nav>
      {pathname === ROUTES.skillAssessments ? (
        <div className="mt-4 overflow-hidden rounded-2xl border border-border bg-accent/40 p-4">
          <p className="font-heading text-lg leading-tight font-bold text-foreground">
            Skills
            <br />
            Build Careers
          </p>
          <p className="mt-1 text-xs text-muted-foreground">Assess. Improve. Achieve.</p>
          <img
            src={skillSidebarArt}
            alt=""
            aria-hidden="true"
            className="mt-3 w-full object-contain"
            loading="lazy"
          />
        </div>
      ) : null}
    </aside>
  );
}

import { NavLink } from "react-router-dom";

import { Logo } from "@/components/layout/Logo";
import { NAV_ITEMS } from "@/components/layout/navItems";
import { useAuth } from "@/features/auth/useAuth";
import { useUnreadMessages } from "@/features/messaging/useUnreadMessages";
import { cn } from "@/lib/utils";

export function Sidebar() {
  const { unreadCount } = useUnreadMessages();
  const { user } = useAuth();
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
    </aside>
  );
}

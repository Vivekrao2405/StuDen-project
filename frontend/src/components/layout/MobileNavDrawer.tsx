import { X } from "lucide-react";
import { useEffect } from "react";
import { NavLink } from "react-router-dom";

import { Logo } from "@/components/layout/Logo";
import { NAV_ITEMS } from "@/components/layout/navItems";
import { useAuth } from "@/features/auth/useAuth";
import { useUnreadMessages } from "@/features/messaging/useUnreadMessages";
import { cn } from "@/lib/utils";

interface MobileNavDrawerProps {
  open: boolean;
  onClose: () => void;
}

/** A lightweight custom slide-over (no new UI primitive needed) — the bottom nav only surfaces 5
 * destinations, this carries the full nav list (same source as the desktop sidebar). */
export function MobileNavDrawer({ open, onClose }: MobileNavDrawerProps) {
  const { unreadCount } = useUnreadMessages();
  const { user } = useAuth();
  const items = NAV_ITEMS.filter((item) => !item.adminOnly || user?.role === "ADMIN");
  useEffect(() => {
    if (!open) return;
    document.body.style.overflow = "hidden";
    return () => {
      document.body.style.overflow = "";
    };
  }, [open]);

  return (
    <div className={cn("fixed inset-0 z-50 lg:hidden", open ? "" : "pointer-events-none")} aria-hidden={!open}>
      <div
        className={cn(
          "absolute inset-0 bg-black/40 transition-opacity",
          open ? "opacity-100" : "opacity-0"
        )}
        onClick={onClose}
      />
      <div
        className={cn(
          "absolute top-0 left-0 flex h-full w-72 max-w-[80vw] flex-col bg-card px-3 py-5 shadow-xl transition-transform duration-200",
          open ? "translate-x-0" : "-translate-x-full"
        )}
      >
        <div className="mb-6 flex items-center justify-between px-2">
          <Logo size="sm" />
          <button
            type="button"
            onClick={onClose}
            aria-label="Close menu"
            className="rounded-full p-1.5 text-muted-foreground hover:bg-muted hover:text-foreground"
          >
            <X className="size-5" />
          </button>
        </div>
        <nav className="flex flex-1 flex-col gap-1">
          {items.map((item) => (
            <NavLink
              key={item.to}
              to={item.to}
              end
              onClick={onClose}
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
      </div>
    </div>
  );
}

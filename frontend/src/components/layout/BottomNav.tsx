import { ClipboardCheck, FolderPlus, GraduationCap, Home, MessageCircle, Plus, ShoppingBag, Trophy } from "lucide-react";
import { NavLink, useNavigate } from "react-router-dom";

import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { useUnreadMessages } from "@/features/messaging/useUnreadMessages";
import { ROUTES } from "@/lib/routes";
import { cn } from "@/lib/utils";

const ITEMS = [
  { label: "Home", to: ROUTES.dashboard, icon: Home },
  { label: "Marketplace", to: ROUTES.marketplace, icon: ShoppingBag },
];

const TRAILING_ITEMS = [
  { label: "Challenges", to: ROUTES.challenges, icon: Trophy },
  { label: "Messages", to: ROUTES.messages, icon: MessageCircle },
];

/** Fixed mobile-only bottom bar: Home, Marketplace, a raised quick-create "+", Challenges,
 * Messages — the 5 most-used destinations. Everything else lives behind MobileHeader's hamburger
 * drawer, so this doesn't duplicate that full nav list. */
export function BottomNav() {
  const navigate = useNavigate();
  const { unreadCount } = useUnreadMessages();

  return (
    <nav className="fixed inset-x-0 bottom-0 z-40 flex h-16 items-center justify-around border-t border-border bg-background/95 backdrop-blur supports-backdrop-filter:bg-background/80 lg:hidden">
      {ITEMS.map((item) => (
        <NavLink
          key={item.to}
          to={item.to}
          end
          className={({ isActive }) =>
            cn(
              "flex flex-col items-center gap-0.5 px-3 py-1 text-[11px] font-medium",
              isActive ? "text-primary" : "text-muted-foreground"
            )
          }
        >
          <item.icon className="size-5" />
          {item.label}
        </NavLink>
      ))}

      <DropdownMenu>
        <DropdownMenuTrigger
          aria-label="Quick create"
          className="-mt-6 flex size-12 items-center justify-center rounded-full bg-primary text-primary-foreground shadow-lg outline-none focus-visible:ring-3 focus-visible:ring-ring/50"
        >
          <Plus className="size-6" />
        </DropdownMenuTrigger>
        <DropdownMenuContent align="center" side="top" sideOffset={12}>
          <DropdownMenuItem onClick={() => navigate(ROUTES.projects)}>
            <FolderPlus /> Add project
          </DropdownMenuItem>
          <DropdownMenuItem onClick={() => navigate(ROUTES.skillAssessments)}>
            <ClipboardCheck /> Take assessment
          </DropdownMenuItem>
          <DropdownMenuItem onClick={() => navigate(ROUTES.profile)}>
            <GraduationCap /> Add education
          </DropdownMenuItem>
          <DropdownMenuItem onClick={() => navigate(ROUTES.challenges)}>
            <Trophy /> Join challenge
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>

      {TRAILING_ITEMS.map((item) => (
        <NavLink
          key={item.to}
          to={item.to}
          end
          className={({ isActive }) =>
            cn(
              "flex flex-col items-center gap-0.5 px-3 py-1 text-[11px] font-medium",
              isActive ? "text-primary" : "text-muted-foreground"
            )
          }
        >
          <span className="relative">
            <item.icon className="size-5" />
            {item.label === "Messages" && unreadCount > 0 ? (
              <span className="absolute -top-1 -right-1.5 flex h-3.5 min-w-3.5 items-center justify-center rounded-full bg-primary px-0.5 text-[8px] font-semibold text-primary-foreground">
                {unreadCount > 9 ? "9+" : unreadCount}
              </span>
            ) : null}
          </span>
          {item.label}
        </NavLink>
      ))}
    </nav>
  );
}

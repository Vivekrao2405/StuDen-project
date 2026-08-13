import { Bell, ClipboardCheck, Home, Inbox, MessageCircle, Settings, ShoppingBag, Trophy, User } from "lucide-react";
import type { LucideIcon } from "lucide-react";

import { ROUTES } from "@/lib/routes";

export interface NavItem {
  label: string;
  to: string;
  icon: LucideIcon;
}

/** The single source of truth for the authenticated app's nav — used by the desktop sidebar and
 * the mobile drawer so both always list the same destinations. */
export const NAV_ITEMS: NavItem[] = [
  { label: "Dashboard", to: ROUTES.dashboard, icon: Home },
  { label: "Marketplace", to: ROUTES.marketplace, icon: ShoppingBag },
  { label: "Requests", to: ROUTES.serviceRequests, icon: Inbox },
  { label: "My Portfolio", to: ROUTES.profile, icon: User },
  { label: "Skill Assessments", to: ROUTES.skillAssessments, icon: ClipboardCheck },
  { label: "Challenges", to: ROUTES.challenges, icon: Trophy },
  { label: "Messages", to: ROUTES.messages, icon: MessageCircle },
  { label: "Notifications", to: ROUTES.notifications, icon: Bell },
  { label: "Settings", to: ROUTES.settings, icon: Settings },
];

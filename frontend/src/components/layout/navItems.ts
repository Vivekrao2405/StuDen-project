import {
  Bell,
  ClipboardCheck,
  Home,
  Inbox,
  MessageCircle,
  Package,
  Settings,
  ShieldCheck,
  ShoppingBag,
  Trophy,
  User,
  Users,
} from "lucide-react";
import type { LucideIcon } from "lucide-react";

import { ROUTES } from "@/lib/routes";

export interface NavItem {
  label: string;
  to: string;
  icon: LucideIcon;
  /** Only rendered for UserRole.ADMIN — the Question Bank management UI is not a normal
   * student-facing surface (spec §17/§47). Consumers (Sidebar, MobileNavDrawer) must filter this
   * out for anyone else; the backend enforces the real boundary independently via
   * @PreAuthorize. */
  adminOnly?: boolean;
}

/** The single source of truth for the authenticated app's nav — used by the desktop sidebar and
 * the mobile drawer so both always list the same destinations. */
export const NAV_ITEMS: NavItem[] = [
  { label: "Dashboard", to: ROUTES.dashboard, icon: Home },
  { label: "Marketplace", to: ROUTES.marketplace, icon: ShoppingBag },
  { label: "Requests", to: ROUTES.serviceRequests, icon: Inbox },
  { label: "Orders", to: ROUTES.orders, icon: Package },
  { label: "My Portfolio", to: ROUTES.profile, icon: User },
  { label: "Skill Assessments", to: ROUTES.skillAssessments, icon: ClipboardCheck },
  { label: "Challenges", to: ROUTES.challenges, icon: Trophy },
  { label: "Messages", to: ROUTES.messages, icon: MessageCircle },
  { label: "Notifications", to: ROUTES.notifications, icon: Bell },
  { label: "Question Bank", to: ROUTES.questionBank, icon: ShieldCheck, adminOnly: true },
  { label: "User Management", to: ROUTES.userManagement, icon: Users, adminOnly: true },
  { label: "Settings", to: ROUTES.settings, icon: Settings },
];

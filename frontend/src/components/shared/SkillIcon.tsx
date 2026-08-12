import {
  Activity,
  Aperture,
  Atom,
  Award,
  BarChart3,
  BookOpen,
  BookText,
  Box,
  Brain,
  Briefcase,
  Brush,
  Calendar,
  Camera,
  ChefHat,
  CircuitBoard,
  Clapperboard,
  ClipboardCheck,
  ClipboardList,
  Code2,
  Contact,
  Cpu,
  CreditCard,
  Database,
  Dna,
  DollarSign,
  Drama,
  Dumbbell,
  Feather,
  FileSpreadsheet,
  FileText,
  Film,
  FlaskConical,
  Globe,
  GraduationCap,
  Guitar,
  Hammer,
  Handshake,
  IdCard,
  Languages,
  LineChart,
  Medal,
  Megaphone,
  MessageCircle,
  Mic,
  Mic2,
  Microscope,
  Music2,
  Music3,
  Network,
  NotebookPen,
  Palette,
  PenTool,
  PersonStanding,
  Piano,
  PieChart,
  Podcast,
  Presentation,
  Radio,
  Rocket,
  Ruler,
  ScrollText,
  Server,
  ShieldAlert,
  ShieldCheck,
  Sigma,
  Sparkles,
  Target,
  Telescope,
  TrendingUp,
  Trophy,
  UserCheck,
  Users2,
  Video,
  Waves,
  Wrench,
  type LucideIcon,
} from "lucide-react";
import { useEffect, useState } from "react";

import type { SkillIconType } from "@/lib/api/types";
import { cn } from "@/lib/utils";

interface SkillIconProps {
  iconSlug: string | null;
  iconType?: SkillIconType;
  className?: string;
}

// The complete, enumerable set of lucide-react icon names the skill catalog ever assigns (see
// backend V3__create_skills_catalog.sql and V4__universal_skill_catalog.sql) plus the
// custom-skill default (Sparkles). Named imports here — rather than a namespace import searched
// by string — keep the bundle from pulling in lucide-react's full icon set.
const GENERIC_ICONS: Record<string, LucideIcon> = {
  Activity,
  Aperture,
  Atom,
  Award,
  BarChart3,
  BookOpen,
  BookText,
  Box,
  Brain,
  Briefcase,
  Brush,
  Calendar,
  Camera,
  ChefHat,
  CircuitBoard,
  Clapperboard,
  ClipboardCheck,
  ClipboardList,
  Contact,
  Cpu,
  CreditCard,
  Database,
  Dna,
  DollarSign,
  Drama,
  Dumbbell,
  Feather,
  FileSpreadsheet,
  FileText,
  Film,
  FlaskConical,
  Globe,
  GraduationCap,
  Guitar,
  Hammer,
  Handshake,
  IdCard,
  Languages,
  LineChart,
  Medal,
  Megaphone,
  MessageCircle,
  Mic,
  Mic2,
  Microscope,
  Music2,
  Music3,
  Network,
  NotebookPen,
  Palette,
  PenTool,
  PersonStanding,
  Piano,
  PieChart,
  Podcast,
  Presentation,
  Radio,
  Rocket,
  Ruler,
  ScrollText,
  Server,
  ShieldAlert,
  ShieldCheck,
  Sigma,
  Sparkles,
  Target,
  Telescope,
  TrendingUp,
  Trophy,
  UserCheck,
  Users2,
  Video,
  Waves,
  Wrench,
};

/**
 * Renders a skill's icon. BRAND icons (official logos, e.g. React, Figma) come from
 * simple-icons' CDN — stable per-slug URLs, no bundled/base64 assets to maintain. LUCIDE icons
 * (everything without a recognizable brand logo — Photography, Football, Public Speaking, custom
 * skills, ...) are looked up by name directly from lucide-react. Either path falls back to a
 * generic icon so a skill can never render broken/blank: no slug, an unknown lucide name, or a
 * failed CDN request all land on the same fallback.
 */
export function SkillIcon({ iconSlug, iconType = "BRAND", className }: SkillIconProps) {
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    setFailed(false);
  }, [iconSlug, iconType]);

  if (iconType === "LUCIDE") {
    const Icon = (iconSlug && GENERIC_ICONS[iconSlug]) || Code2;
    return (
      <span className={cn("flex items-center justify-center text-muted-foreground", className)}>
        <Icon className="size-full" strokeWidth={1.75} />
      </span>
    );
  }

  if (!iconSlug || failed) {
    return (
      <span className={cn("flex items-center justify-center text-muted-foreground", className)}>
        <Code2 className="size-full" strokeWidth={1.75} />
      </span>
    );
  }

  return (
    <img
      src={`https://cdn.simpleicons.org/${iconSlug}`}
      alt=""
      aria-hidden="true"
      className={cn("object-contain", className)}
      onError={() => setFailed(true)}
    />
  );
}

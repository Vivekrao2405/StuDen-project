import {
  Camera,
  Code2,
  GraduationCap,
  Music2,
  Palette,
  PenTool,
  PersonStanding,
  Sparkles,
  Users,
  Video,
  Wand2,
} from "lucide-react";

import type { CarouselItem } from "@/components/shared/CategoryCarousel";

export const CATEGORIES: CarouselItem[] = [
  { id: "web-development", label: "Web Development", icon: Code2 },
  { id: "tutoring", label: "Tutoring", icon: GraduationCap },
  { id: "video-editing", label: "Video Editing", icon: Video },
  { id: "graphic-design", label: "Graphic Design", icon: Palette },
  { id: "photography", label: "Photography", icon: Camera },
  { id: "music", label: "Music", icon: Music2 },
  { id: "dance", label: "Dance", icon: PersonStanding },
  { id: "crocheting", label: "Crocheting", icon: Sparkles },
  { id: "modelling", label: "Modelling", icon: Users },
  { id: "makeup", label: "Makeup", icon: Wand2 },
  { id: "calligraphy", label: "Calligraphy", icon: PenTool },
];

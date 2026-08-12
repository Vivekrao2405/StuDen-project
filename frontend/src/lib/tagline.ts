import type { SkillResponse } from "@/lib/api/types";

/**
 * Deterministic, rule-based tagline generator — no external AI calls. Since it's a pure function
 * of the student's actual selected skills, the tagline always reflects (and updates with) exactly
 * what they've chosen; nothing is invented or persisted separately from the skill selection itself.
 *
 * StuDen spans far more than tech/design/business skills (see the universal skill catalog in
 * backend V4__universal_skill_catalog.sql), so CATEGORY_RULES covers every catalog category —
 * sports, arts, languages, teaching, etc. — not just developer-flavored ones.
 *
 * Each entry also carries `highlights`: substrings of its own tagline text to render in the
 * accent color on the share profile card, so the highlighted phrase always changes together with
 * the tagline itself instead of being guessed separately at render time.
 */

interface TaglineDef {
  tagline: string;
  highlights: string[];
}

export interface TaglineResult {
  text: string;
  highlights: string[];
}

interface NamedCombo extends TaglineDef {
  names: string[];
}

// Checked first: specific, well-known skill combinations get a tailored tagline before falling
// back to the more generic category-based rules below.
const NAMED_COMBOS: NamedCombo[] = [
  {
    names: ["react", "javascript", "typescript", "node.js"],
    tagline: "Turning ideas into clean, modern and impactful digital experiences.",
    highlights: ["clean, modern and impactful digital experiences"],
  },
  {
    names: ["python", "sql", "power bi", "excel"],
    tagline: "Turning data into insights that drive smarter decisions.",
    highlights: ["insights that drive smarter decisions"],
  },
  {
    names: ["figma", "ui/ux design", "graphic design"],
    tagline: "Designing experiences that are simple, intuitive and memorable.",
    highlights: ["simple, intuitive and memorable"],
  },
  {
    names: ["java", "spring boot", "postgresql"],
    tagline: "Building reliable systems that turn complex problems into practical solutions.",
    highlights: ["complex problems into practical solutions"],
  },
];

interface CategoryRule extends TaglineDef {
  when: (categories: Set<string>) => boolean;
}

// Checked in order — first match wins, so more specific combinations (e.g. Full Stack, or a
// cross-domain pairing like Marketing + Content Creation) are listed before the single-category
// rules they'd otherwise also satisfy.
const CATEGORY_RULES: CategoryRule[] = [
  {
    when: (c) => c.has("Frontend") && (c.has("Backend") || c.has("Database")),
    tagline: "Building complete, end-to-end products — from interface to backend.",
    highlights: ["from interface to backend"],
  },
  {
    when: (c) => c.has("Business") && c.has("Media & Content"),
    tagline: "Turning ideas into content that connects people with brands and communities.",
    highlights: ["content that connects people with brands and communities"],
  },
  {
    when: (c) => c.has("Academic") && c.has("Teaching"),
    tagline: "Making complex concepts simple, clear and understandable.",
    highlights: ["simple, clear and understandable"],
  },
  {
    when: (c) => c.has("Photography & Video") && c.has("Writing"),
    tagline: "Capturing stories and turning moments into content that resonates.",
    highlights: ["moments into content that resonates"],
  },
  {
    when: (c) => c.has("AI/ML"),
    tagline: "Applying machine learning to build smarter, data-driven solutions.",
    highlights: ["smarter, data-driven solutions"],
  },
  {
    when: (c) => c.has("Data/Analytics"),
    tagline: "Turning data into clear insights and smarter decisions.",
    highlights: ["clear insights and smarter decisions"],
  },
  {
    when: (c) => c.has("Design"),
    tagline: "Designing intuitive experiences that make complex ideas feel simple.",
    highlights: ["complex ideas feel simple"],
  },
  {
    when: (c) => c.has("Backend"),
    tagline: "Building reliable backend systems for real-world applications.",
    highlights: ["real-world applications"],
  },
  {
    when: (c) => c.has("Frontend"),
    tagline: "Building modern digital experiences with clean, user-focused interfaces.",
    highlights: ["clean, user-focused interfaces"],
  },
  {
    when: (c) => c.has("Mobile"),
    tagline: "Crafting smooth, native-feeling mobile experiences.",
    highlights: ["native-feeling mobile experiences"],
  },
  {
    when: (c) => c.has("DevOps/Cloud"),
    tagline: "Building and shipping reliable systems at scale.",
    highlights: ["reliable systems at scale"],
  },
  {
    when: (c) => c.has("Cybersecurity"),
    tagline: "Protecting systems and data through thoughtful, secure engineering.",
    highlights: ["thoughtful, secure engineering"],
  },
  {
    when: (c) => c.has("Database"),
    tagline: "Designing solid data foundations that other systems can rely on.",
    highlights: ["other systems can rely on"],
  },
  {
    when: (c) => c.has("Programming"),
    tagline: "Solving problems through clean, thoughtful code.",
    highlights: ["clean, thoughtful code"],
  },
  {
    when: (c) => c.has("Photography & Video"),
    tagline: "Capturing stories and transforming moments into compelling visual experiences.",
    highlights: ["compelling visual experiences"],
  },
  {
    when: (c) => c.has("Writing"),
    tagline: "Turning ideas into words that inform, persuade and inspire.",
    highlights: ["inform, persuade and inspire"],
  },
  {
    when: (c) => c.has("Media & Content"),
    tagline: "Creating content that informs, entertains and connects.",
    highlights: ["informs, entertains and connects"],
  },
  {
    when: (c) => c.has("Arts & Performance"),
    tagline: "Creating experiences through music, performance and creativity.",
    highlights: ["music, performance and creativity"],
  },
  {
    when: (c) => c.has("Sports & Fitness"),
    tagline: "Passionate about the game and helping others grow through sport.",
    highlights: ["helping others grow through sport"],
  },
  {
    when: (c) => c.has("Leadership"),
    tagline: "Turning ideas into impact through communication and leadership.",
    highlights: ["impact through communication and leadership"],
  },
  {
    when: (c) => c.has("Teaching"),
    tagline: "Helping others learn and grow through clear, patient teaching.",
    highlights: ["clear, patient teaching"],
  },
  {
    when: (c) => c.has("Business"),
    tagline: "Turning ideas into strategies that drive real business impact.",
    highlights: ["real business impact"],
  },
  {
    when: (c) => c.has("Academic"),
    tagline: "Exploring ideas and building deep subject expertise.",
    highlights: ["deep subject expertise"],
  },
  {
    when: (c) => c.has("Languages"),
    tagline: "Bridging cultures and people through language and communication.",
    highlights: ["language and communication"],
  },
  {
    when: (c) => c.has("Practical Skills"),
    tagline: "Building and fixing real things with hands-on, practical skill.",
    highlights: ["hands-on, practical skill"],
  },
  {
    when: (c) => c.has("Career Skills"),
    tagline: "Helping others navigate their path to a stronger career.",
    highlights: ["a stronger career"],
  },
  {
    when: (c) => c.has("Creative"),
    tagline: "Bringing creativity and craft to every project.",
    highlights: ["creativity and craft"],
  },
];

const NEUTRAL_TAGLINE: TaglineDef = {
  tagline: "Building skills and gaining real experience, one project at a time.",
  highlights: ["real experience"],
};

function toResult(def: TaglineDef): TaglineResult {
  return { text: def.tagline, highlights: def.highlights };
}

export function generateTagline(skills: Pick<SkillResponse, "name" | "category">[]): TaglineResult {
  if (skills.length === 0) return toResult(NEUTRAL_TAGLINE);

  const names = new Set(skills.map((s) => s.name.toLowerCase()));
  for (const combo of NAMED_COMBOS) {
    if (combo.names.every((n) => names.has(n))) {
      return toResult(combo);
    }
  }

  const categories = new Set(skills.map((s) => s.category));
  for (const rule of CATEGORY_RULES) {
    if (rule.when(categories)) {
      return toResult(rule);
    }
  }

  return toResult(NEUTRAL_TAGLINE);
}

// Frontend mirror of backend com.studen.common.tag.TagParser (LANGUAGE-TOPIC1-TOPIC2-... hyphenated
// tag convention). Used purely for *display* — deriving a human-readable topic badge for a resource
// card — never for scoring/ranking, which stays entirely server-side (ResourceMatchingService).
export interface ParsedTag {
  language: string | null;
  topics: string[];
}

export function parseTag(rawTag: string | null | undefined): ParsedTag {
  const trimmed = rawTag?.trim() ?? "";
  if (!trimmed) return { language: null, topics: [] };

  const segments = trimmed
    .toLowerCase()
    .split("-")
    .map((s) => s.trim())
    .filter(Boolean);
  if (segments.length === 0) return { language: null, topics: [] };

  const [language, ...rest] = segments;
  return { language, topics: Array.from(new Set(rest)) };
}

export function topicLabel(topic: string): string {
  return topic
    .split(" ")
    .map((word) => (word.length > 0 ? word[0].toUpperCase() + word.slice(1) : word))
    .join(" ");
}

// Picks the most relevant topic badge for a resource card: prefer a topic the resource shares with
// the group's weak topics, then any topic of the resource's own, then its bare language.
export function primaryTopicForResource(tags: string[], weakTopics: Set<string>): string | null {
  for (const tag of tags) {
    const match = parseTag(tag).topics.find((topic) => weakTopics.has(topic));
    if (match) return match;
  }
  for (const tag of tags) {
    const [first] = parseTag(tag).topics;
    if (first) return first;
  }
  for (const tag of tags) {
    const { language } = parseTag(tag);
    if (language) return language;
  }
  return null;
}

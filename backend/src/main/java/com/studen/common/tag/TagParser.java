package com.studen.common.tag;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The single place that understands the app-wide tag convention: {@code LANGUAGE-TOPIC1-TOPIC2-...}
 * (see {@code Question.tags}/{@code Resource.tags}, both free-form hyphenated strings). Splits
 * purely on {@code '-'}, first segment is the language/primary skill, every segment after it is its
 * own topic — never re-merged, never treated as one composite topic. Every caller that needs
 * language/topic granularity (resource matching today; anything tag-hierarchy-aware later) must go
 * through this rather than re-splitting strings locally, so the convention only lives in one place.
 */
public final class TagParser {

    private TagParser() {
    }

    public static ParsedTag parse(String rawTag) {
        if (rawTag == null) {
            return new ParsedTag(null, null, List.of());
        }
        String trimmed = rawTag.trim();
        if (trimmed.isEmpty()) {
            return new ParsedTag(rawTag, null, List.of());
        }

        String normalized = trimmed.toLowerCase(Locale.ROOT);
        List<String> segments = new ArrayList<>();
        for (String segment : normalized.split("-")) {
            String cleaned = segment.trim();
            if (!cleaned.isEmpty()) {
                segments.add(cleaned);
            }
        }
        // Malformed: only hyphens, or hyphens with blank segments ("-", "--", " - ").
        if (segments.isEmpty()) {
            return new ParsedTag(rawTag, null, List.of());
        }

        String language = segments.get(0);
        Set<String> topics = new LinkedHashSet<>(segments.subList(1, segments.size()));
        return new ParsedTag(rawTag, language, List.copyOf(topics));
    }

    // True if any tag in the set parses out the given topic segment (e.g. tags containing
    // "python-lists" match topic "lists"). The single predicate behind both
    // ResourceMatchingService.topicBreakdown()'s per-topic resource counting and the roadmap's
    // topic-to-resource matching, so it has exactly one implementation.
    public static boolean anyTagMatchesTopic(Set<String> tags, String topic) {
        return tags.stream().anyMatch(tag -> parse(tag).topics().contains(topic));
    }
}

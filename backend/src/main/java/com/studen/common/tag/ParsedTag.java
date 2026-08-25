package com.studen.common.tag;

import java.util.List;

// original is the exact input string handed to TagParser.parse (unmodified, including null) — the
// only field callers should ever persist or compare against a live Question/Resource.tags entry.
// language/topics are lowercase+trimmed, derived by hyphen-splitting per the LANGUAGE-TOPIC1-
// TOPIC2... convention (see TagParser). A tag with no topic segment (e.g. "python", or malformed
// input) has language set (if any segment could be found) and an empty topics list.
public record ParsedTag(String original, String language, List<String> topics) {
}

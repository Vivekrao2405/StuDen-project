package com.studen.placement;

import java.util.UUID;

// Result of a grouped count query, used to attach child counts (skills per role, questions per
// assessment, modules per series) to a list response in one extra query instead of one query per
// row. Populated via a JPQL constructor expression, never exposed over HTTP.
public record IdCountView(UUID id, long count) {
}

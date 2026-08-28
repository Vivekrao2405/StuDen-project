package com.studen.resource;

import java.util.UUID;

// Lets other feature packages react to a resource being marked COMPLETED (e.g. com.studen.calendar
// auto-completing a linked scheduled session) without com.studen.resource depending on them —
// ResourceService.complete() invokes every registered listener synchronously, in the same
// transaction, right after the progress row is written. Spring injects an empty list when no
// listener bean exists, so this package has zero required dependents.
public interface ResourceCompletionListener {

    void onCompleted(UUID studentId, UUID resourceId);
}

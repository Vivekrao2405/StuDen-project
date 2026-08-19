package com.studen.integrity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

// Batched delivery (goal #28 -- don't send one request per keystroke-adjacent browser event).
// Capped at 100 per call as a defensive bound, not a real-world limit -- the frontend flushes
// every ~8s so a batch this size would be extraordinary.
public record IntegrityEventBatchRequest(
        @NotEmpty @Size(max = 100) @Valid List<IntegrityEventRequest> events) {
}

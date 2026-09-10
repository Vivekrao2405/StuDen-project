/**
 * Phase 6 AI Coach — per-question coaching (Hint/Explain Pattern/Debug My Code/Explain Concept/
 * Solution) attached to an existing {@code com.studen.practical.PracticalAttemptQuestion}. Reuses
 * the existing coding/execution infrastructure entirely (never re-executes or re-scores student
 * code itself); this package only adds the LLM-backed coaching layer on top, calling the OpenAI
 * API via {@link com.studen.aicoach.OpenAiCoachClient}.
 */
package com.studen.aicoach;

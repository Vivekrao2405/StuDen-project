package com.studen.integrity;

// Per-assessment integrity configuration -- resolved from the `integrityPolicy` key inside
// PracticalAssessment.configurationJson (see IntegrityPolicyResolver), never a new column
// (mirrors how sqlOrderedComparison already lives in that same free-form JSON blob). Defaults to
// fully permissive so every pre-Phase-7.6 assessment is unaffected.
public record IntegrityPolicy(boolean allowCopy, boolean allowPaste, boolean allowCut, boolean requireFullscreen) {

    public static final IntegrityPolicy PERMISSIVE_DEFAULT = new IntegrityPolicy(true, true, true, false);
}

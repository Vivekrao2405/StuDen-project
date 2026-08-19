package com.studen.communication;

// `marketing` mirrors CommunicationCampaign.marketing so the previewed count/sample apply the
// exact same marketing-opt-out exclusion the actual send will apply (see
// AudienceSpecificationBuilder's hard floor) — the estimate must never be computed by different
// logic than the real recipient resolution. Deliberately a boxed Boolean, not a primitive: Jackson
// 3's record deserialization here 400s ("Malformed request body") when a JSON field is missing
// and the matching constructor parameter is a primitive with no value to substitute — live-
// verified while wiring this field in. A boxed Boolean simply binds null instead — callers must
// use isMarketing(), not marketing(), to get the null-safe "missing = not a marketing campaign"
// default (a record accessor's return type can't differ from its component's, so this can't be
// exposed under the marketing() name itself).
public record AudiencePreviewRequest(String filterJson, Boolean marketing) {

    public boolean isMarketing() {
        return Boolean.TRUE.equals(marketing);
    }
}

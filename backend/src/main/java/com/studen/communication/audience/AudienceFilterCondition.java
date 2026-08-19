package com.studen.communication.audience;

import java.util.Map;

/**
 * One leaf condition. {@code params} is deliberately a flat string map (e.g. {@code
 * {"days":"30"}}, {@code {"skillId":"<uuid>"}}, {@code {"skillIds":"<uuid1>,<uuid2>"}}, {@code
 * {"minScore":"70"}}) rather than typed fields — every {@link AudienceFilterField} interprets its
 * own params defensively (missing/malformed -> a documented safe default), so a bad value never
 * 500s the whole audience resolution.
 */
public record AudienceFilterCondition(AudienceFilterField field, Map<String, String> params)
        implements AudienceFilterNode {

    public String param(String key) {
        return params == null ? null : params.get(key);
    }
}

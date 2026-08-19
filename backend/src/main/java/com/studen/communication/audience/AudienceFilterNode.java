package com.studen.communication.audience;

/**
 * A small recursive AND/OR filter tree — the entire non-technical audience model the Filter
 * Builder UI produces and {@link AudienceSpecificationBuilder} compiles into a JPA
 * {@code Specification<User>}. No SQL is ever exposed to or accepted from the admin; the frontend
 * only ever sends this shape as JSON, stored verbatim as {@code filter_json} and re-parsed by
 * {@link AudienceFilterParser} on every resolve/preview/send.
 */
public sealed interface AudienceFilterNode permits AudienceFilterGroup, AudienceFilterCondition {
}

package com.studen.marketplace;

/** The "currently available / not currently available" marketplace filter — distinct from
 * {@code AvailabilityOption} (what kind of work a student is open to). For students this filters
 * {@code StudentPortfolio.available}; for services it filters {@code ServiceListing.available}
 * (independent of a service's DRAFT/ACTIVE/INACTIVE status, which is always hard-filtered to
 * ACTIVE for search regardless of this value). */
public enum AvailabilityFilter {
    AVAILABLE,
    NOT_AVAILABLE
}

package com.studen.marketplace;

/** The "currently available / not currently available" marketplace filter — distinct from
 * {@code AvailabilityOption} (what kind of work a student is open to). Applies to student
 * results only; services are always filtered to ACTIVE regardless of this. */
public enum AvailabilityFilter {
    AVAILABLE,
    NOT_AVAILABLE
}

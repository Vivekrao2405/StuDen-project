package com.studen.marketplace;

/** The Marketplace "All / Students / Services" tab filter. {@code null} (not requested) means
 * both sources are searched — the pre-6.3 default behavior, unchanged for any caller that
 * doesn't pass this param. */
public enum ResultTypeFilter {
    STUDENT,
    SERVICE
}

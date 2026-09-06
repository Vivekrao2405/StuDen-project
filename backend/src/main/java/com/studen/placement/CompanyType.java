package com.studen.placement;

// An enum, not a reference table: this codebase has no reference-table precedent — every
// closed set of values (QuestionType, ResourceType, PracticalType, ...) is an @Enumerated(STRING)
// column. Stored as text, so adding a value later needs no migration.
public enum CompanyType {
    SERVICE_BASED,
    PRODUCT_BASED,
    STARTUP,
    CONSULTING,
    GCC,
    OTHER
}

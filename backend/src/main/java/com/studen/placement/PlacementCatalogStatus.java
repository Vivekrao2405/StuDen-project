package com.studen.placement;

// Enable/disable for admin-managed reference data (roles, companies). Deliberately NOT the
// DRAFT/PUBLISHED/ARCHIVED lifecycle used for authored content — a role or company is either
// offered to students or it is not; there is no draft or review stage for it.
public enum PlacementCatalogStatus {
    ACTIVE,
    INACTIVE
}

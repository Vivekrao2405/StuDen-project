package com.studen.practical;

// The practical "subject" — distinct from workspaceType (com.studen.practical.WorkspaceType),
// which is the UI/interaction the student gets. One practicalType can map to different
// workspaceTypes over time (e.g. UI_UX -> UI_UX_WORKSPACE today, could add a design-implementation
// variant later) — see spec §57.
public enum PracticalType {
    CODING,
    WEB_DEVELOPMENT,
    UI_UX,
    SQL,
    DATA_ANALYSIS,
    EXCEL,
    POWER_BI,
    OTHER_PRACTICAL
}

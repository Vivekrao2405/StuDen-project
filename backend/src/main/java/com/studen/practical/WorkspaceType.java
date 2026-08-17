package com.studen.practical;

/**
 * Drives which student-facing workspace component renders — see the frontend's workspace
 * registry (frontend/src/pages/practical/workspaces/registry.ts). Only CODE_EDITOR, WEB_EDITOR,
 * SQL_EDITOR, and UI_UX_WORKSPACE have a dedicated component in this phase; every other value
 * renders the generic file/link/text submission workspace. That's a real, working fallback, not a
 * placeholder — adding a dedicated component for e.g. REACT_EDITOR later is a one-line registry
 * change, not a redesign (spec §63/§66).
 */
public enum WorkspaceType {
    CODE_EDITOR,
    SQL_EDITOR,
    WEB_EDITOR,
    REACT_EDITOR,
    MERN_WORKSPACE,
    UI_UX_WORKSPACE,
    DATA_ANALYSIS_WORKSPACE,
    EXCEL_WORKSPACE,
    POWER_BI_WORKSPACE,
    FILE_SUBMISSION,
    LINK_SUBMISSION
}

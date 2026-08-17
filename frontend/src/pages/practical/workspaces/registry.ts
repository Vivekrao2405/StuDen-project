import type { ComponentType } from "react";

import type { WorkspaceType } from "@/lib/api/practicalTypes";
import { CodingWorkspace } from "@/pages/practical/workspaces/CodingWorkspace";
import { GenericSubmissionWorkspace } from "@/pages/practical/workspaces/GenericSubmissionWorkspace";
import { SqlWorkspace } from "@/pages/practical/workspaces/SqlWorkspace";
import type { WorkspaceProps } from "@/pages/practical/workspaces/types";
import { UiUxWorkspace } from "@/pages/practical/workspaces/UiUxWorkspace";
import { WebEditorWorkspace } from "@/pages/practical/workspaces/WebEditorWorkspace";

// The single place that maps a workspaceType to its component — no if/else-if chain anywhere
// else in the app (spec §50/§62). Only these four have a dedicated experience; every other
// workspaceType intentionally falls back to GenericSubmissionWorkspace, a real (not fake)
// submission form — see that component's doc comment for why.
export const WORKSPACE_REGISTRY: Record<WorkspaceType, ComponentType<WorkspaceProps>> = {
  CODE_EDITOR: CodingWorkspace,
  WEB_EDITOR: WebEditorWorkspace,
  SQL_EDITOR: SqlWorkspace,
  UI_UX_WORKSPACE: UiUxWorkspace,
  REACT_EDITOR: GenericSubmissionWorkspace,
  MERN_WORKSPACE: GenericSubmissionWorkspace,
  DATA_ANALYSIS_WORKSPACE: GenericSubmissionWorkspace,
  EXCEL_WORKSPACE: GenericSubmissionWorkspace,
  POWER_BI_WORKSPACE: GenericSubmissionWorkspace,
  FILE_SUBMISSION: GenericSubmissionWorkspace,
  LINK_SUBMISSION: GenericSubmissionWorkspace,
};

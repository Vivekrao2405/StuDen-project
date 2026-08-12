// Matches the native-<select> styling already used in SkillPicker.tsx (SELECT_CLASS) — kept as
// its own local copy rather than importing that one, since SkillPicker is existing, working code
// this feature shouldn't touch.
export const MARKETPLACE_SELECT_CLASS =
  "h-9 w-full min-w-0 rounded-lg border border-input bg-transparent px-2.5 py-1 text-sm transition-colors outline-none placeholder:text-muted-foreground focus-visible:border-ring focus-visible:ring-3 focus-visible:ring-ring/50 disabled:pointer-events-none disabled:cursor-not-allowed disabled:opacity-50 dark:bg-input/30";

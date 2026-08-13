import { useContext } from "react";

import { FocusedResourceContext } from "@/features/notifications/focusedResourceContextDefinition";

export function useFocusedResource() {
  const ctx = useContext(FocusedResourceContext);
  if (!ctx) {
    throw new Error("useFocusedResource must be used within a FocusedResourceProvider");
  }
  return ctx;
}

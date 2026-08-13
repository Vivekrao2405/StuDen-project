import { createContext } from "react";

export interface FocusedResource {
  type: string;
  resourceId: string;
}

export interface FocusedResourceContextValue {
  setFocusedResource: (resource: FocusedResource | null) => void;
}

export const FocusedResourceContext = createContext<FocusedResourceContextValue | null>(null);

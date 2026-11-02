"use client";

import { useSyncExternalStore } from "react";
import { getServerSessionState, getSessionState, subscribe } from "./session";

export function useSession() {
  return useSyncExternalStore(subscribe, getSessionState, getServerSessionState);
}

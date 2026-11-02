"use client";

import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from "react";

type ToastFn = (message: string) => void;
const ToastContext = createContext<ToastFn>(() => undefined);

/** Small dark message at the bottom of the screen (design: .toast), hidden after 2.4 s. */
export function ToastProvider({ children }: { children: ReactNode }) {
  const [message, setMessage] = useState<string | null>(null);
  const timer = useRef<ReturnType<typeof setTimeout>>(undefined);

  const toast = useCallback<ToastFn>((text) => {
    setMessage(text);
    clearTimeout(timer.current);
    timer.current = setTimeout(() => setMessage(null), 2400);
  }, []);

  useEffect(() => () => clearTimeout(timer.current), []);

  return (
    <ToastContext.Provider value={toast}>
      {children}
      <div
        role="status"
        aria-live="polite"
        className="fixed left-1/2 z-50 max-w-[calc(100%-32px)] -translate-x-1/2 rounded-[10px] bg-ink px-4 py-2.5 text-sm text-white"
        style={{ bottom: "calc(24px + env(safe-area-inset-bottom, 0px))" }}
        hidden={!message}
      >
        {message}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  return useContext(ToastContext);
}

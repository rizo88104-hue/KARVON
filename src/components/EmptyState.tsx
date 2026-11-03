import type { ReactNode } from "react";

/** Centered message with an optional action, for empty lists and "not found" states. */
export function EmptyState({ title, text, action }: { title: string; text?: ReactNode; action?: ReactNode }) {
  return (
    <div className="card flex flex-col items-center gap-3 px-6 py-14 text-center">
      <h2 className="m-0 text-[20px] font-bold">{title}</h2>
      {text && <p className="m-0 max-w-[48ch] text-muted">{text}</p>}
      {action}
    </div>
  );
}

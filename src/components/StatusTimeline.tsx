import { useLocale, useTranslations } from "next-intl";
import type { TimelineStep } from "@/api/types";
import { formatDateTime } from "@/lib/format";

/** Vertical order timeline from OrderDetailResponse.timeline (state DONE / CURRENT / TODO). */
export function StatusTimeline({ steps }: { steps: TimelineStep[] }) {
  const t = useTranslations("timeline");
  const s = useTranslations("status");
  const locale = useLocale();
  return (
    <ol className="m-0 list-none p-0">
      {steps.map((step, i) => {
        const state = step.state ?? "TODO";
        const last = i === steps.length - 1;
        return (
          <li key={`${step.status}-${i}`} className="relative grid grid-cols-[18px_1fr] gap-3 pb-4">
            {!last && (
              <span
                aria-hidden="true"
                className={`absolute top-4 bottom-0 left-[8px] w-0.5 ${state === "DONE" ? "bg-navy" : "bg-line"}`}
              />
            )}
            <span
              aria-hidden="true"
              className={`relative z-[1] mt-1 h-[18px] w-[18px] rounded-full border-2 ${
                state === "DONE"
                  ? "border-navy bg-navy"
                  : state === "CURRENT"
                    ? "border-orange bg-orange shadow-[0_0_0_4px_rgba(255,153,0,0.2)]"
                    : "border-line bg-white"
              }`}
            />
            <div className={state === "TODO" ? "text-muted" : ""}>
              <span className={state === "CURRENT" ? "font-bold" : "font-medium"}>{step.status ? s(step.status) : ""}</span>
              <span className="sr-only"> — {t(state === "DONE" ? "done" : state === "CURRENT" ? "current" : "todo")}</span>
              {step.at && <div className="num text-[13px] text-muted">{formatDateTime(step.at, locale)}</div>}
              {step.comment && <div className="text-[13px] text-subtle">{step.comment}</div>}
            </div>
          </li>
        );
      })}
    </ol>
  );
}

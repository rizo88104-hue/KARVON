"use client";

import { useTranslations } from "next-intl";

type Props = {
  value: number;
  onChange: (value: number) => void;
  min?: number;
  max?: number;
  disabled?: boolean;
};

/** − value + stepper. */
export function QuantityInput({ value, onChange, min = 1, max = 999, disabled }: Props) {
  const t = useTranslations("quantity");
  const set = (next: number) => onChange(Math.min(max, Math.max(min, next)));
  const btn =
    "grid w-10 place-items-center bg-grey-bg text-[18px] font-bold disabled:cursor-not-allowed disabled:opacity-40";
  return (
    <div className="inline-flex h-10 overflow-hidden rounded-sm border border-line" role="group" aria-label={t("label")}>
      <button type="button" className={btn} onClick={() => set(value - 1)} disabled={disabled || value <= min} aria-label={t("decrease")}>
        −
      </button>
      <input
        className="num w-12 border-0 text-center outline-none"
        inputMode="numeric"
        aria-label={t("label")}
        value={value}
        disabled={disabled}
        onChange={(e) => {
          const n = parseInt(e.target.value.replace(/\D/g, ""), 10);
          if (!Number.isNaN(n)) set(n);
        }}
      />
      <button type="button" className={btn} onClick={() => set(value + 1)} disabled={disabled || value >= max} aria-label={t("increase")}>
        +
      </button>
    </div>
  );
}

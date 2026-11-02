import { useTranslations } from "next-intl";
import { formatAmount } from "@/lib/format";

type Props = {
  amount: number | null | undefined;
  /** Previous price, shown struck through (design: .old). */
  old?: number | null;
  size?: "sm" | "md" | "lg" | "xl";
  className?: string;
};

const SIZES = {
  sm: "text-[15px]",
  md: "text-[16px] sm:text-[19px]",
  lg: "text-[24px]",
  xl: "text-[30px] sm:text-[34px] leading-none",
};

/** A price in UZS: "149 000 so'm". */
export function PriceTag({ amount, old, size = "md", className = "" }: Props) {
  const t = useTranslations("money");
  return (
    <span className={`inline-flex flex-col ${className}`}>
      <span className={`num font-bold tracking-[-0.01em] ${SIZES[size]}`}>
        {t("som", { amount: formatAmount(amount) })}
      </span>
      {old != null && old > (amount ?? 0) && (
        <span className="num text-[13px] text-muted line-through">{t("som", { amount: formatAmount(old) })}</span>
      )}
    </span>
  );
}

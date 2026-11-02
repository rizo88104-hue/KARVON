import { useTranslations } from "next-intl";
import type { OrderStatus } from "@/api/types";

const ATTENTION: OrderStatus[] = ["AWAITING_PAYMENT", "AWAITING_SHIPPING_PAYMENT", "OUT_OF_STOCK", "PRICE_CHANGED"];

export function StatusBadge({ status }: { status?: OrderStatus }) {
  const t = useTranslations("status");
  if (!status) return null;
  const tone = ATTENTION.includes(status)
    ? "bg-orange text-ink"
    : status === "DELIVERED"
      ? "bg-navy text-white"
      : status === "CANCELLED"
        ? "bg-grey-bg text-muted"
        : "bg-grey-bg text-ink";
  return <span className={`inline-block rounded-[6px] px-2 py-0.5 text-[12px] font-bold ${tone}`}>{t(status)}</span>;
}

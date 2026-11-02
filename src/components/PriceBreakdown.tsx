import { useTranslations } from "next-intl";
import { formatAmount, formatDecimal } from "@/lib/format";

type Breakdown = {
  goodsUzs?: number;
  serviceFeeUzs?: number;
  shippingUzs?: number;
  totalUzs?: number;
  chargeableWeightKg?: number;
};

/** Goods + service fee + shipping = total, as dashed rows (design of the old demo, E-Market colors). */
export function PriceBreakdown({ price, title }: { price: Breakdown | null | undefined; title?: string }) {
  const t = useTranslations("breakdown");
  const m = useTranslations("money");
  if (!price) return null;
  const som = (v?: number) => m("som", { amount: formatAmount(v) });
  return (
    <div className="flex flex-col gap-1.5 text-[14px]">
      {title && <b className="text-[15px]">{title}</b>}
      <Row label={t("goods")} value={som(price.goodsUzs)} />
      <Row label={t("serviceFee")} value={som(price.serviceFeeUzs)} />
      <Row label={t("shipping")} value={som(price.shippingUzs)} />
      {price.chargeableWeightKg != null && (
        <span className="text-[13px] text-muted">{t("weight", { kg: formatDecimal(price.chargeableWeightKg) })}</span>
      )}
      <div className="mt-1 flex justify-between gap-3 border-t border-dashed border-line pt-2 text-[16px] font-bold">
        <span>{t("total")}</span>
        <span className="num">{som(price.totalUzs)}</span>
      </div>
    </div>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between gap-3">
      <span className="text-subtle">{label}</span>
      <span className="num">{value}</span>
    </div>
  );
}

import { useTranslations } from "next-intl";

/** Orange "-NN%" badge in the top-left corner of a product image (design: .badge). */
export function DiscountBadge({ percent }: { percent: number | null }) {
  const t = useTranslations("productCard");
  if (!percent) return null;
  return (
    <span className="num absolute top-2.5 left-2.5 z-[1] rounded-[6px] bg-orange px-2 py-[3px] text-[12px] font-bold text-ink">
      {t("discount", { percent })}
    </span>
  );
}

import { useTranslations } from "next-intl";

/** Orange "-NN%" badge on a product image (design: .badge); top-left unless `position` places it elsewhere. */
export function DiscountBadge({
  percent,
  position = "top-2.5 left-2.5",
}: {
  percent: number | null;
  position?: string;
}) {
  const t = useTranslations("productCard");
  if (!percent) return null;
  return (
    <span className={`num absolute ${position} z-[1] rounded-[6px] bg-orange px-2 py-[3px] text-[12px] font-bold text-ink`}>
      {t("discount", { percent })}
    </span>
  );
}

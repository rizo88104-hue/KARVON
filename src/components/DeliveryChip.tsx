"use client";

import { useTranslations } from "next-intl";
import { usePricingSettings } from "@/lib/queries";
import { getDeliveryDays } from "@/lib/site";

/**
 * Yellow chip with the delivery days ("12–18 kun") from the pricing settings, under the price on product cards.
 * The settings query is shared by all cards. While it loads, an empty chip keeps the card height steady.
 */
export function DeliveryChip() {
  const t = useTranslations("productCard");
  const { data } = usePricingSettings();
  const days = getDeliveryDays(data);
  return (
    <span
      className={`num mt-1 inline-flex min-h-[20px] items-center self-start rounded-[4px] px-1.5 text-[12px] font-medium text-ink ${
        days ? "bg-highlight" : ""
      }`}
      title={t("deliveryLabel")}
    >
      {days ? t("deliveryDays", { days }) : null}
    </span>
  );
}

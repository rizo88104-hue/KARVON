"use client";

import { useTranslations } from "next-intl";
import { useState } from "react";
import type { ProductCard } from "@/api/types";
import { useErrorMessage } from "@/lib/use-error-message";
import { useFavorites } from "@/lib/favorites";
import { useToast } from "./Toast";

/**
 * Heart button that adds the product to "Sevimlilar" or removes it. `card` is what a guest's browser list shows.
 * "overlay": round white button on a product photo; "inline": outlined button with a text, for the product page.
 */
export function FavoriteButton({
  card,
  variant = "overlay",
  className = "",
}: {
  card: ProductCard;
  variant?: "overlay" | "inline";
  className?: string;
}) {
  const t = useTranslations("favorites");
  const toast = useToast();
  const errorMessage = useErrorMessage();
  const favorites = useFavorites();
  const active = favorites.has(card.externalId);
  // Bumped on every click to replay the heart's pop animation (.heart-pop).
  const [pops, setPops] = useState(0);

  async function onClick() {
    setPops((n) => n + 1);
    try {
      await favorites.toggle(card);
      toast(active ? t("removed") : t("added"));
    } catch (error) {
      toast(errorMessage(error));
    }
  }

  const label = active ? t("remove") : t("add");
  const heart = (
    <svg
      key={pops}
      viewBox="0 0 24 24"
      width={variant === "overlay" ? 18 : 20}
      height={variant === "overlay" ? 18 : 20}
      fill={active ? "currentColor" : "none"}
      stroke="currentColor"
      strokeWidth="2"
      strokeLinejoin="round"
      aria-hidden="true"
      className={pops > 0 ? "heart-pop" : undefined}
    >
      <path d="M12 20.5s-7.5-4.6-9.3-9.2C1.4 8 3.4 4.5 7 4.5c2 0 3.5 1.1 5 3 1.5-1.9 3-3 5-3 3.6 0 5.6 3.5 4.3 6.8-1.8 4.6-9.3 9.2-9.3 9.2z" />
    </svg>
  );

  if (variant === "inline") {
    return (
      <button
        type="button"
        onClick={onClick}
        aria-pressed={active}
        className={`btn btn-ghost w-full gap-2 border border-line ${active ? "text-heart" : ""} ${className}`}
      >
        {heart}
        {active ? t("inFavorites") : t("add")}
      </button>
    );
  }

  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={active}
      aria-label={label}
      title={label}
      className={`grid h-9 w-9 place-items-center rounded-full border-0 bg-white/90 p-0 shadow-[0_2px_8px_rgba(17,17,17,0.15)] transition-colors hover:bg-white ${
        active ? "text-heart" : "text-ink hover:text-heart"
      } ${className}`}
    >
      {heart}
    </button>
  );
}

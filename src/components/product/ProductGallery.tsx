"use client";

import { useTranslations } from "next-intl";
import { useState } from "react";
import { DiscountBadge } from "@/components/DiscountBadge";
import { ProductImage } from "@/components/ProductImage";

/** Main photo with thumbnails. `highlight` (the chosen SKU's photo) is shown first when set. */
export function ProductGallery({
  images,
  title,
  highlight,
  discount,
}: {
  images: string[];
  title: string;
  highlight?: string | null;
  discount?: number | null;
}) {
  const t = useTranslations("product");
  const tc = useTranslations("productCard");
  const all = highlight && !images.includes(highlight) ? [highlight, ...images] : images;
  const [picked, setPicked] = useState<string | null>(null);
  const [lastHighlight, setLastHighlight] = useState(highlight);

  // A new SKU photo replaces the manual choice.
  if (highlight !== lastHighlight) {
    setLastHighlight(highlight);
    setPicked(null);
  }
  const current = picked ?? highlight ?? all[0] ?? null;
  const currentIndex = Math.max(0, all.indexOf(current ?? ""));

  return (
    <div className="flex flex-col gap-3 min-[901px]:flex-row-reverse" aria-label={t("gallery")} role="group">
      <div className="relative w-full flex-1">
        <DiscountBadge percent={discount ?? null} />
        {/* key: a new photo re-mounts this block, which fades it in (.photo-in). */}
        <div key={current ?? ""} className="photo-in">
          <ProductImage
            src={current}
            alt={t("image", { title, index: currentIndex + 1 })}
            placeholder={tc("noImage")}
            sizes="(max-width: 900px) 100vw, 480px"
            priority
            className="aspect-square w-full rounded-md"
          />
        </div>
      </div>
      {all.length > 1 && (
        <div className="no-scrollbar flex gap-2 overflow-x-auto min-[901px]:w-[72px] min-[901px]:flex-none min-[901px]:flex-col">
          {all.slice(0, 8).map((src, i) => (
            <button
              key={src}
              type="button"
              onClick={() => setPicked(src)}
              aria-label={t("image", { title, index: i + 1 })}
              aria-pressed={src === current}
              className={`w-[64px] flex-none overflow-hidden rounded-sm border-2 p-0 transition-[border-color,opacity] duration-200 min-[901px]:w-full ${
                src === current ? "border-navy" : "border-transparent opacity-80 hover:opacity-100"
              }`}
            >
              <ProductImage src={src} alt="" placeholder="" sizes="72px" className="aspect-square w-full" />
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

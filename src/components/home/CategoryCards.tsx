import Image from "next/image";
import type { Category } from "@/api/types";
import { CategoryIcon } from "@/components/CategoryIcon";
import { categoryHref, categoryName } from "@/components/layout/categories";
import { Link } from "@/i18n/navigation";

/** Top categories as cards: the cover photo when the category has one, otherwise its icon on grey. */
export function CategoryCards({ categories, locale, label }: { categories: Category[]; locale: string; label: string }) {
  return (
    <nav aria-label={label} className="grid grid-cols-2 gap-2.5 min-[561px]:grid-cols-3 min-[561px]:gap-4 min-[901px]:grid-cols-5">
      {categories.map((c) => {
        const name = categoryName(c, locale);
        return (
          <Link
            key={c.slug}
            href={categoryHref(c, locale)}
            className="group relative flex aspect-[8/5] items-end overflow-hidden rounded-md bg-grey-bg"
          >
            {c.imageUrl ? (
              <>
                <Image
                  src={c.imageUrl}
                  alt=""
                  fill
                  loading="lazy"
                  sizes="(max-width: 560px) 50vw, (max-width: 900px) 33vw, 240px"
                  className="object-cover transition-transform duration-300 group-hover:scale-105"
                />
                <span className="absolute inset-0 bg-linear-to-t from-black/70 via-black/15 to-transparent" />
              </>
            ) : (
              <CategoryIcon category={c} size={56} className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-2/3 text-navy/30" />
            )}
            <span
              className={`relative flex items-center gap-2 p-3 text-[15px] font-bold ${c.imageUrl ? "text-white" : "text-navy"}`}
            >
              <span
                className={`grid h-8 w-8 flex-none place-items-center rounded-full ${c.imageUrl ? "bg-white/20 backdrop-blur-sm" : "bg-white"}`}
              >
                <CategoryIcon category={c} size={18} />
              </span>
              {name}
            </span>
          </Link>
        );
      })}
    </nav>
  );
}

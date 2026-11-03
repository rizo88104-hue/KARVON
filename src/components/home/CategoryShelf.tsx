import type { Category, ProductCard as ProductCardData } from "@/api/types";
import { CategoryIcon } from "@/components/CategoryIcon";
import { categoryHref, categoryName } from "@/components/layout/categories";
import { ProductCard, ProductGrid } from "@/components/ProductCard";
import { Link } from "@/i18n/navigation";

// One full row per screen width: 4 on phones (2x2), 3 on tablets, 4 on small desktops, 5 on wide screens.
const ITEM_CLASS = ["", "", "", "min-[561px]:max-[900px]:hidden", "max-[1100px]:hidden"];

/** Home page block of one category: its name, a "see all" link to the category search and up to 5 products. */
export function CategoryShelf({
  category,
  products,
  locale,
  seeAll,
}: {
  category: Category;
  products: ProductCardData[];
  locale: string;
  seeAll: string;
}) {
  const name = categoryName(category, locale);
  const href = categoryHref(category, locale);
  return (
    <section className="mt-9" aria-labelledby={`shelf-${category.slug}`}>
      <div className="mb-3.5 flex items-baseline justify-between gap-3">
        <h2 id={`shelf-${category.slug}`} className="m-0 flex items-center gap-2.5 text-[22px] font-bold tracking-[-0.01em]">
          <span className="grid h-9 w-9 flex-none place-items-center self-center rounded-full bg-grey-bg text-navy">
            <CategoryIcon category={category} size={19} />
          </span>
          <Link href={href} className="hover:text-navy">
            {name}
          </Link>
        </h2>
        <Link href={href} className="text-[14px] font-medium whitespace-nowrap hover:text-navy hover:underline">
          {seeAll}
        </Link>
      </div>
      <ProductGrid label={name}>
        {products.slice(0, 5).map((p, i) => (
          <div key={p.externalId} className={`flex flex-col [&>*]:flex-1 ${ITEM_CLASS[i]}`}>
            <ProductCard product={p} />
          </div>
        ))}
      </ProductGrid>
    </section>
  );
}

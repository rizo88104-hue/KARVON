"use client";

import { useInfiniteQuery, useQuery } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { useSearchParams } from "next/navigation";
import { useEffect, useState } from "react";
import { api } from "@/api/client";
import { unwrap } from "@/api/errors";
import { EmptyState } from "@/components/EmptyState";
import { ProductCard, ProductGrid } from "@/components/ProductCard";
import { useRouter } from "@/i18n/navigation";
import { looksLikeLink } from "@/lib/site";
import { useErrorMessage } from "@/lib/use-error-message";

const SORTS = ["RELEVANCE", "SALES", "PRICE_ASC", "PRICE_DESC"] as const;
type Sort = (typeof SORTS)[number];
const MAX_PAGE = 99; // backend limit

function toNumber(v: string | null) {
  if (!v) return undefined;
  const n = Number(v);
  return Number.isFinite(n) && n >= 0 ? n : undefined;
}

export function SearchResults() {
  const params = useSearchParams();
  const q = (params.get("q") ?? "").trim();
  if (!q) {
    return <EmptyQuery />;
  }
  return looksLikeLink(q) ? <ResolveLink key={q} url={q} /> : <CatalogSearch key={q} q={q} />;
}

function EmptyQuery() {
  const t = useTranslations("search");
  return <EmptyState title={t("emptyQueryTitle")} text={t("emptyQueryText")} />;
}

/** A Taobao link: ask the backend for the product id and open the product page. */
function ResolveLink({ url }: { url: string }) {
  const t = useTranslations("search");
  const router = useRouter();
  const resolved = useQuery({
    queryKey: ["resolve-link", url],
    queryFn: async () => unwrap(await api.POST("/api/v1/catalog/resolve-link", { body: { url } })),
    retry: false,
  });

  const externalId = resolved.data?.externalId;
  useEffect(() => {
    if (externalId) router.replace(`/product/${encodeURIComponent(externalId)}`);
  }, [externalId, router]);

  if (resolved.isError || (resolved.isSuccess && !externalId)) {
    return <EmptyState title={t("linkNotFoundTitle")} text={t("linkNotFoundText")} />;
  }
  return (
    <p className="py-14 text-center text-muted" role="status">
      {t("resolving")}
    </p>
  );
}

function CatalogSearch({ q }: { q: string }) {
  const t = useTranslations("search");
  const tc = useTranslations("common");
  const errorMessage = useErrorMessage();
  const params = useSearchParams();
  const router = useRouter();

  const sortParam = params.get("sort") as Sort | null;
  const sort: Sort = sortParam && SORTS.includes(sortParam) ? sortParam : "RELEVANCE";
  const minPrice = toNumber(params.get("minPrice"));
  const maxPrice = toNumber(params.get("maxPrice"));

  const setParams = (changes: Record<string, string | undefined>) => {
    const next = new URLSearchParams(params.toString());
    for (const [k, v] of Object.entries(changes)) {
      if (v) next.set(k, v);
      else next.delete(k);
    }
    router.replace(`/search?${next.toString()}`, { scroll: false });
  };

  const results = useInfiniteQuery({
    queryKey: ["search", q, sort, minPrice, maxPrice],
    initialPageParam: 0,
    queryFn: async ({ pageParam }) =>
      unwrap(
        await api.GET("/api/v1/catalog/search", {
          params: { query: { q, sort, page: pageParam, minPrice, maxPrice } },
        }),
      ),
    getNextPageParam: (last) => {
      const page = last.page ?? 0;
      return page + 1 < (last.totalPages ?? 0) && page + 1 <= MAX_PAGE ? page + 1 : undefined;
    },
  });

  const items = results.data?.pages.flatMap((p) => p.content ?? []) ?? [];
  const total = results.data?.pages[0]?.totalElements ?? 0;

  return (
    <>
      <div className="mb-4 flex flex-wrap items-baseline gap-x-3 gap-y-1">
        <h1 className="m-0 text-[24px] font-bold tracking-[-0.01em]">{t("title")}</h1>
        <span className="text-muted">{t("query", { q })}</span>
      </div>

      <div className="card mb-5 flex flex-col gap-3 p-4">
        <div className="grid grid-cols-1 gap-2 sm:grid-cols-[110px_1fr] sm:gap-3">
          <span className="pt-2 text-[14px] text-muted">{t("sort")}</span>
          <div className="flex flex-wrap gap-2">
            {SORTS.map((s) => (
              <button
                key={s}
                type="button"
                className="chip"
                aria-pressed={sort === s}
                onClick={() => setParams({ sort: s === "RELEVANCE" ? undefined : s })}
              >
                {t(`sort${s}`)}
              </button>
            ))}
          </div>
        </div>
        <PriceFilter
          key={`${minPrice}-${maxPrice}`}
          min={minPrice}
          max={maxPrice}
          onApply={(min, max) => setParams({ minPrice: min, maxPrice: max })}
        />
      </div>

      {results.isPending ? (
        <p className="py-10 text-center text-muted" role="status">
          {tc("loading")}
        </p>
      ) : results.isError ? (
        <EmptyState
          title={t("error")}
          text={errorMessage(results.error)}
          action={
            <button type="button" className="btn btn-navy" onClick={() => results.refetch()}>
              {tc("retry")}
            </button>
          }
        />
      ) : items.length === 0 ? (
        <EmptyState title={t("emptyTitle")} text={t("emptyText")} />
      ) : (
        <>
          <p className="mt-0 mb-3 font-bold" role="status">
            {t("results", { count: total })}
          </p>
          <ProductGrid>
            {items.map((p) => (
              <ProductCard key={p.externalId} product={p} />
            ))}
          </ProductGrid>
          {results.hasNextPage && (
            <div className="mt-6 flex justify-center">
              <button
                type="button"
                className="btn btn-outline"
                disabled={results.isFetchingNextPage}
                onClick={() => results.fetchNextPage()}
              >
                {results.isFetchingNextPage ? tc("loading") : tc("loadMore")}
              </button>
            </div>
          )}
        </>
      )}
    </>
  );
}

function PriceFilter({
  min,
  max,
  onApply,
}: {
  min?: number;
  max?: number;
  onApply: (min?: string, max?: string) => void;
}) {
  const t = useTranslations("search");
  const [from, setFrom] = useState(min?.toString() ?? "");
  const [to, setTo] = useState(max?.toString() ?? "");
  const clean = (v: string) => v.replace(/\D/g, "") || undefined;

  return (
    <form
      className="grid grid-cols-1 gap-2 sm:grid-cols-[110px_1fr] sm:gap-3"
      onSubmit={(e) => {
        e.preventDefault();
        onApply(clean(from), clean(to));
      }}
    >
      <span className="pt-2 text-[14px] text-muted">{t("price")}</span>
      <div className="flex flex-wrap items-center gap-2">
        <input
          className="input num h-9 w-28"
          inputMode="numeric"
          placeholder={t("priceFrom")}
          aria-label={`${t("price")} ${t("priceFrom")}`}
          value={from}
          onChange={(e) => setFrom(e.target.value)}
        />
        <span className="text-muted">—</span>
        <input
          className="input num h-9 w-28"
          inputMode="numeric"
          placeholder={t("priceTo")}
          aria-label={`${t("price")} ${t("priceTo")}`}
          value={to}
          onChange={(e) => setTo(e.target.value)}
        />
        <button type="submit" className="btn btn-navy h-9 px-4 text-[14px]">
          {t("apply")}
        </button>
        {(min !== undefined || max !== undefined) && (
          <button type="button" className="link-btn" onClick={() => onApply(undefined, undefined)}>
            {t("reset")}
          </button>
        )}
      </div>
    </form>
  );
}

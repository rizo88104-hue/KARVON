"use client";

import { useInfiniteQuery, type InfiniteData } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { useState } from "react";
import { api } from "@/api/client";
import { unwrap } from "@/api/errors";
import type { ProductCard as ProductCardData, SearchPage } from "@/api/types";
import { ProductCard, ProductGrid } from "./ProductCard";

/** Products shown at first and added by every "Yana ko'rsatish" click (full rows of 2, 3 and 4 columns). */
export const FEED_FIRST = 12;
export const FEED_STEP = 12;
const MAX_PAGE = 99; // backend limit

export type FeedSort = "RELEVANCE" | "SALES" | "PRICE_ASC" | "PRICE_DESC";
export type FeedQuery = { q: string; sort: FeedSort; minPrice?: number; maxPrice?: number };

/** What ProductFeed needs from a paged query: the loaded pages and a way to load the next one. */
type Feed = {
  data?: { pages: { content?: ProductCardData[] }[] };
  hasNextPage: boolean;
  isFetchingNextPage: boolean;
  fetchNextPage: () => Promise<unknown>;
};

function hasPageAfter(page: SearchPage, pageNumber: number) {
  return pageNumber + 1 < (page.totalPages ?? 0) && pageNumber + 1 <= MAX_PAGE;
}

function searchPage(q: string, sort: FeedSort, page: number, minPrice?: number, maxPrice?: number) {
  return api
    .GET("/api/v1/catalog/search", { params: { query: { q, sort, page, minPrice, maxPrice } } })
    .then(unwrap);
}

/** The catalog search as pages (search page). */
export function useSearchFeed(query: FeedQuery) {
  return useInfiniteQuery({
    queryKey: ["search", query.q, query.sort, query.minPrice, query.maxPrice],
    initialPageParam: 0,
    queryFn: ({ pageParam }) => searchPage(query.q, query.sort, pageParam, query.minPrice, query.maxPrice),
    getNextPageParam: (last) => (hasPageAfter(last, last.page ?? 0) ? (last.page ?? 0) + 1 : undefined),
  });
}

export type MixSource = { keywordZh: string; firstPage: SearchPage };
type MixPage = { content: ProductCardData[]; more: boolean };

/** One page of every category, interleaved: 1st of each category, then 2nd of each, and so on. */
function mix(pages: SearchPage[], pageNumber: number): MixPage {
  const lists = pages.map((p) => p.content ?? []);
  const content: ProductCardData[] = [];
  const longest = Math.max(0, ...lists.map((l) => l.length));
  for (let i = 0; i < longest; i++) {
    for (const list of lists) if (i < list.length) content.push(list[i]);
  }
  return { content, more: pages.some((p) => hasPageAfter(p, pageNumber)) };
}

/**
 * The home page feed: best sellers of all categories mixed together. Page N of the feed is page N of every
 * category that still has one; the server sends page 0, the browser loads the rest.
 */
export function useMixedFeed(sources: MixSource[]) {
  return useInfiniteQuery({
    queryKey: ["home-mix", sources.map((s) => s.keywordZh)],
    initialPageParam: 0,
    queryFn: async ({ pageParam }) => {
      const open = sources.filter((s) => pageParam < (s.firstPage.totalPages ?? 0));
      const pages = await Promise.all(open.map((s) => searchPage(s.keywordZh, "SALES", pageParam)));
      return mix(pages, pageParam);
    },
    getNextPageParam: (last, _all, lastPageParam) => (last.more ? lastPageParam + 1 : undefined),
    initialData: {
      pages: [mix(sources.map((s) => s.firstPage), 0)],
      pageParams: [0],
    } as InfiniteData<MixPage, number>,
  });
}

/**
 * A grid that shows the first FEED_FIRST products and a "Yana ko'rsatish" button, like the marketplace feeds:
 * each click shows FEED_STEP more, loading the next page from the API when the loaded ones run out.
 */
export function ProductFeed({ feed, label }: { feed: Feed; label?: string }) {
  const tc = useTranslations("common");
  const [visible, setVisible] = useState(FEED_FIRST);
  // A product can come twice (two categories match it, or the list moved between pages): show it once.
  const seen = new Set<string>();
  const items = (feed.data?.pages.flatMap((p) => p.content ?? []) ?? []).filter((p) => {
    const id = p.externalId ?? "";
    if (seen.has(id)) return false;
    seen.add(id);
    return true;
  });
  const shown = items.slice(0, visible);
  const more = items.length > visible || feed.hasNextPage;

  async function showMore() {
    const want = visible + FEED_STEP;
    if (items.length < want && feed.hasNextPage) await feed.fetchNextPage();
    setVisible(want);
  }

  return (
    <>
      <ProductGrid label={label}>
        {shown.map((p) => (
          <ProductCard key={p.externalId} product={p} />
        ))}
      </ProductGrid>
      {more && (
        <div className="mt-6 flex justify-center">
          <button
            type="button"
            className="btn btn-outline min-w-[220px]"
            disabled={feed.isFetchingNextPage}
            onClick={showMore}
          >
            {feed.isFetchingNextPage ? tc("loading") : tc("loadMore")}
          </button>
        </div>
      )}
    </>
  );
}

/** Home page feed of all categories mixed, with "Yana ko'rsatish". */
export function MixedFeed({ sources, label }: { sources: MixSource[]; label: string }) {
  const feed = useMixedFeed(sources);
  return <ProductFeed feed={feed} label={label} />;
}

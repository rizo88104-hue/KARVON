"use client";

import { useQuery } from "@tanstack/react-query";
import { useLocale, useTranslations } from "next-intl";
import { api } from "@/api/client";
import { unwrap } from "@/api/errors";
import { EmptyState } from "@/components/EmptyState";
import { ProductCard, ProductGrid } from "@/components/ProductCard";
import { Link } from "@/i18n/navigation";
import { favoriteKeys, useFavorites, useGuestFavorites } from "@/lib/favorites";
import { useErrorMessage } from "@/lib/use-error-message";
import { useSession } from "@/lib/use-session";

/**
 * "Sevimlilar" page. Signed in: the account's list (current prices from the backend). Guest: the list kept in this
 * browser, with a note that logging in keeps it on every device. A removed product disappears at once.
 */
export function FavoritesView() {
  const t = useTranslations("favorites");
  const locale = useLocale();
  const errorMessage = useErrorMessage();
  const status = useSession().status;
  const authed = status === "authenticated";
  const favorites = useFavorites();
  const guest = useGuestFavorites();

  const list = useQuery({
    queryKey: favoriteKeys.list(locale),
    queryFn: async () => unwrap(await api.GET("/api/v1/me/favorites")),
    enabled: authed,
  });

  if (status === "unknown" || (authed && list.isPending)) {
    return <p className="text-muted">{t("loading")}</p>;
  }
  if (authed && list.isError) {
    return <EmptyState title={t("loadError")} text={errorMessage(list.error)} />;
  }

  const cards = (authed ? (list.data ?? []) : guest).filter((c) => favorites.has(c.externalId));

  return (
    <>
      <div className="mb-4 flex flex-wrap items-baseline justify-between gap-2">
        <h1 className="m-0 text-[26px] font-extrabold tracking-[-0.02em]">{t("title")}</h1>
        {cards.length > 0 && <span className="text-[14px] text-muted">{t("count", { count: cards.length })}</span>}
      </div>

      {!authed && cards.length > 0 && (
        <p className="mt-0 mb-4 rounded-md bg-grey-bg px-4 py-3 text-[14px] text-subtle">
          {t.rich("guestNote", {
            login: (chunks) => (
              <Link href="/login?next=/favorites" className="font-bold text-navy underline">
                {chunks}
              </Link>
            ),
          })}
        </p>
      )}

      {cards.length === 0 ? (
        <EmptyState
          title={t("emptyTitle")}
          text={t.rich("emptyText", {
            // The ♡ of the text, drawn like the product cards' heart; the glyph stays for screen readers.
            heart: (chunks) => (
              <span className="inline-flex translate-y-[3px] text-heart" role="img" aria-label={String(chunks)}>
                <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" aria-hidden="true">
                  <path d="M12 20.5s-7.5-4.6-9.3-9.2C1.4 8 3.4 4.5 7 4.5c2 0 3.5 1.1 5 3 1.5-1.9 3-3 5-3 3.6 0 5.6 3.5 4.3 6.8-1.8 4.6-9.3 9.2-9.3 9.2z" />
                </svg>
              </span>
            ),
          })}
          action={
            <Link href="/" className="btn btn-orange">
              {t("emptyCta")}
            </Link>
          }
        />
      ) : (
        <ProductGrid label={t("title")}>
          {cards.map((c) => (
            <ProductCard key={c.externalId} product={c} />
          ))}
        </ProductGrid>
      )}
    </>
  );
}

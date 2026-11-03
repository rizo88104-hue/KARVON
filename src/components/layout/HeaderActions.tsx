"use client";

import { useTranslations } from "next-intl";
import { Link } from "@/i18n/navigation";
import { useCart } from "@/lib/queries";
import { useSession } from "@/lib/use-session";
import { NotificationBell } from "./NotificationBell";

/** "Kirish"/"Kabinet", the notification bell (signed in) and "Savat" (design: .head-actions). Labels hide below 560px. */
export function HeaderActions() {
  const t = useTranslations("header");
  const session = useSession();
  const { data: cart } = useCart();
  const authed = session.status === "authenticated";
  const count = authed ? (cart?.itemCount ?? 0) : 0;
  const item = "btn btn-ghost h-10 gap-2";

  return (
    <nav className="ml-auto flex items-center gap-1.5 min-[901px]:ml-3" aria-label={t("userNav")}>
      <Link href={authed ? "/account/orders" : "/login"} className={item} aria-label={authed ? t("account") : t("login")}>
        <svg
          viewBox="0 0 24 24"
          width="20"
          height="20"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
          aria-hidden="true"
        >
          <circle cx="12" cy="8" r="4" />
          <path d="M4 21c1.5-4 4.5-6 8-6s6.5 2 8 6" />
        </svg>
        <span className="hidden min-[561px]:inline">{authed ? t("account") : t("login")}</span>
      </Link>
      {authed && <NotificationBell />}
      <Link href="/cart" className={`${item} relative`} aria-label={count ? t("cartCount", { count }) : t("cart")}>
        <svg
          viewBox="0 0 24 24"
          width="20"
          height="20"
          fill="none"
          stroke="currentColor"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
          aria-hidden="true"
        >
          <path d="M3 4h2l2.4 11h11l2-8H6.3" />
          <circle cx="9.5" cy="19.5" r="1.5" />
          <circle cx="17.5" cy="19.5" r="1.5" />
        </svg>
        <span className="hidden min-[561px]:inline">{t("cart")}</span>
        {count > 0 && (
          <span className="num absolute top-0.5 left-6 min-w-[18px] rounded-full bg-orange px-1 text-center text-[11px] leading-[18px] font-bold text-ink">
            {count}
          </span>
        )}
      </Link>
    </nav>
  );
}

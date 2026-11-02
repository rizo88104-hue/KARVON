"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { useEffect, type ReactNode } from "react";
import { Link, usePathname, useRouter } from "@/i18n/navigation";
import { logout } from "@/lib/session";
import { useSession } from "@/lib/use-session";

const NAV = [
  { href: "/account/orders", key: "orders" },
  { href: "/account/wallet", key: "wallet" },
  { href: "/account/addresses", key: "addresses" },
  { href: "/account/settings", key: "settings" },
] as const;

/** Side navigation of the account pages; sends guests to /login and back here afterwards. */
export function AccountShell({ children }: { children: ReactNode }) {
  const t = useTranslations("account");
  const tc = useTranslations("common");
  const session = useSession();
  const pathname = usePathname();
  const router = useRouter();
  const qc = useQueryClient();

  useEffect(() => {
    if (session.status === "guest") router.replace(`/login?next=${encodeURIComponent(pathname)}`);
  }, [session.status, pathname, router]);

  if (session.status !== "authenticated") {
    return (
      <p className="py-14 text-center text-muted" role="status">
        {tc("loading")}
      </p>
    );
  }

  const name = session.user?.fullName?.trim();
  return (
    <div className="grid grid-cols-1 items-start gap-4 min-[901px]:grid-cols-[220px_minmax(0,1fr)]">
      <aside className="card flex flex-col gap-1 p-2.5 min-[901px]:sticky min-[901px]:top-3">
        <div className="border-b border-line px-2.5 pt-2 pb-3">
          <b className="block">{name ? t("hello", { name }) : t("helloNoName")}</b>
          <span className="num text-[13px] text-muted">{session.user?.phone}</span>
        </div>
        <nav aria-label={t("nav")} className="no-scrollbar flex gap-1 overflow-x-auto min-[901px]:flex-col">
          {NAV.map((item) => {
            const active = pathname.startsWith(item.href);
            return (
              <Link
                key={item.href}
                href={item.href}
                aria-current={active ? "page" : undefined}
                className={`rounded-sm px-3 py-2.5 text-[14px] whitespace-nowrap ${
                  active ? "bg-grey-bg font-bold text-navy" : "hover:bg-grey-bg"
                }`}
              >
                {t(item.key)}
              </Link>
            );
          })}
          <button
            type="button"
            className="rounded-sm border-0 bg-transparent px-3 py-2.5 text-left text-[14px] whitespace-nowrap text-muted hover:bg-grey-bg"
            onClick={async () => {
              await logout();
              qc.clear();
              router.replace("/");
            }}
          >
            {t("logout")}
          </button>
        </nav>
      </aside>
      <div className="min-w-0">{children}</div>
    </div>
  );
}

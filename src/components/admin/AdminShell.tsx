"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { Suspense, useEffect, type ReactNode } from "react";
import { EmptyState } from "@/components/EmptyState";
import { LanguageSwitch } from "@/components/layout/LanguageSwitch";
import { Link, usePathname, useRouter } from "@/i18n/navigation";
import { ADMIN_SECTIONS, isStaff, visibleSections } from "@/lib/admin/roles";
import { logout } from "@/lib/session";
import { useSession } from "@/lib/use-session";

/**
 * Staff panel frame: dark top bar, left menu with only the sections of the user's roles (a scrolling strip on
 * phones). Guests go to /login; customers without a staff role see "no access".
 */
export function AdminShell({ children }: { children: ReactNode }) {
  const t = useTranslations("admin");
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

  const user = session.user;
  const sections = visibleSections(user);
  const current = ADMIN_SECTIONS.find((s) => pathname === s.href || pathname.startsWith(`${s.href}/`));
  const allowed = !current || sections.some((s) => s.key === current.key);

  return (
    <div className="flex min-h-dvh flex-col bg-[#f6f7f8]">
      <header className="bg-ink text-white">
        <div className="flex min-h-12 flex-wrap items-center gap-x-4 gap-y-1 px-4 py-2">
          <Link href="/admin" className="text-[18px] font-bold whitespace-nowrap">
            E-Market <span className="text-orange">{t("brandSuffix")}</span>
          </Link>
          <div className="ml-auto flex flex-wrap items-center gap-x-4 gap-y-1 text-[13px]">
            <Suspense>
              <LanguageSwitch />
            </Suspense>
            <Link href="/" className="opacity-80 hover:opacity-100 hover:underline">
              {t("toSite")}
            </Link>
            <span className="num opacity-80">{user?.phone}</span>
            <button
              type="button"
              className="border-0 bg-transparent p-0 text-white opacity-80 hover:opacity-100 hover:underline"
              onClick={async () => {
                await logout();
                qc.clear();
                router.replace("/login?next=/admin");
              }}
            >
              {t("logout")}
            </button>
          </div>
        </div>
      </header>

      {!isStaff(user) ? (
        <main className="p-4">
          <EmptyState
            title={t("noAccessTitle")}
            text={t("noAccessText")}
            action={
              <Link href="/" className="btn btn-orange">
                {t("toSite")}
              </Link>
            }
          />
        </main>
      ) : (
        <div className="flex flex-1 flex-col min-[901px]:flex-row">
          <nav
            aria-label={t("menu")}
            className="no-scrollbar flex gap-1 overflow-x-auto border-b border-line bg-white p-2 min-[901px]:w-[210px] min-[901px]:flex-none min-[901px]:flex-col min-[901px]:border-r min-[901px]:border-b-0 min-[901px]:p-3"
          >
            {sections.map((s) => {
              const active = current?.key === s.key;
              return (
                <Link
                  key={s.key}
                  href={s.href}
                  aria-current={active ? "page" : undefined}
                  className={`rounded-sm px-3 py-2 text-[14px] whitespace-nowrap ${
                    active ? "bg-orange font-bold text-ink" : "hover:bg-grey-bg"
                  }`}
                >
                  {t(`nav.${s.key}`)}
                </Link>
              );
            })}
          </nav>
          <main className="min-w-0 flex-1 p-3 min-[561px]:p-5">
            {allowed ? (
              children
            ) : (
              <EmptyState
                title={t("noAccessTitle")}
                text={t("sectionForbidden")}
                action={
                  sections[0] && (
                    <Link href={sections[0].href} className="btn btn-orange">
                      {t(`nav.${sections[0].key}`)}
                    </Link>
                  )
                }
              />
            )}
          </main>
        </div>
      )}
    </div>
  );
}

/** /admin itself: open the first section the user may see. */
export function AdminIndex() {
  const session = useSession();
  const router = useRouter();
  const first = visibleSections(session.user)[0];
  useEffect(() => {
    if (first) router.replace(first.href);
  }, [first, router]);
  return null;
}

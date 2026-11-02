"use client";

import { useQuery } from "@tanstack/react-query";
import { useLocale, useTranslations } from "next-intl";
import { useState } from "react";
import { api } from "@/api/client";
import { isApiError, unwrap } from "@/api/errors";
import { StatusBadge } from "@/components/account/StatusBadge";
import { EmptyState } from "@/components/EmptyState";
import { Link } from "@/i18n/navigation";
import { FORM_QUERY, hasRole } from "@/lib/admin/roles";
import { formatDateTime } from "@/lib/format";
import { useErrorMessage } from "@/lib/use-error-message";
import { useSession } from "@/lib/use-session";
import { WalletAdjustForm } from "./FinanceView";
import { AdminPageHeader, Loading, Pager, Section, TableWrap, useMoney } from "./ui";

type Found = { id: string; phone: string; name?: string };

/**
 * Search by phone. ADMIN searches all users (GET /admin/users is ADMIN-only); OPERATOR finds customers through
 * their orders (GET /admin/orders?phone=), so customers without orders are found only by an ADMIN.
 */
export function CustomersView() {
  const t = useTranslations("admin.customers");
  const ta = useTranslations("admin");
  const session = useSession();
  const errorMessage = useErrorMessage();
  const isAdmin = hasRole(session.user, "ADMIN");
  const [draft, setDraft] = useState("");
  const [phone, setPhone] = useState("");

  const results = useQuery({
    queryKey: ["admin", "customer-search", phone, isAdmin],
    enabled: phone.length >= 3,
    queryFn: async (): Promise<Found[]> => {
      if (isAdmin) {
        const page = unwrap(
          await api.GET("/api/v1/admin/users", {
            params: { query: { phone, pageable: { page: 0, size: 20 } } },
            querySerializer: FORM_QUERY,
          }),
        );
        return (page.content ?? []).map((u) => ({ id: u.id ?? "", phone: u.phone ?? "", name: u.fullName ?? undefined }));
      }
      const page = unwrap(await api.GET("/api/v1/admin/orders", { params: { query: { phone, page: 0, size: 50 } } }));
      const byId = new Map<string, Found>();
      for (const o of page.content ?? []) {
        if (o.customerId && !byId.has(o.customerId)) byId.set(o.customerId, { id: o.customerId, phone: o.customerPhone ?? "" });
      }
      return [...byId.values()];
    },
  });

  return (
    <>
      <AdminPageHeader title={ta("nav.customers")} />
      <form
        className="card mb-4 flex flex-wrap items-end gap-2 p-3"
        onSubmit={(e) => {
          e.preventDefault();
          setPhone(draft.replace(/[^\d+]/g, ""));
        }}
      >
        <label className="label min-w-[220px] flex-1">
          <span className="hint">{t("searchLabel")}</span>
          <input className="input num h-9" inputMode="tel" placeholder="+998" value={draft} onChange={(e) => setDraft(e.target.value)} />
        </label>
        <button type="submit" className="btn btn-navy btn-sm">
          {t("search")}
        </button>
      </form>
      {!isAdmin && <p className="mt-0 mb-3 text-[13px] text-muted">{t("operatorHint")}</p>}

      {phone.length < 3 ? null : results.isPending ? (
        <Loading />
      ) : results.isError ? (
        <EmptyState title={ta("loadError")} text={errorMessage(results.error)} />
      ) : !results.data.length ? (
        <EmptyState title={t("notFound")} />
      ) : (
        <Section>
          <ul className="m-0 list-none p-0">
            {results.data.map((c) => (
              <li key={c.id} className="border-b border-line last:border-b-0">
                <Link href={`/admin/customers/${c.id}`} className="flex flex-wrap items-center gap-3 px-4 py-3 hover:bg-grey-bg/50">
                  <b className="num">{c.phone}</b>
                  {c.name && <span className="text-subtle">{c.name}</span>}
                  <span className="ml-auto text-[13px] text-navy">{t("open")}</span>
                </Link>
              </li>
            ))}
          </ul>
        </Section>
      )}
    </>
  );
}

export function CustomerView({ id }: { id: string }) {
  const t = useTranslations("admin.customers");
  const ta = useTranslations("admin");
  const tw = useTranslations("wallet");
  const tr = useTranslations("admin.roles");
  const tn = useTranslations("admin.notifications");
  const locale = useLocale();
  const money = useMoney();
  const session = useSession();
  const errorMessage = useErrorMessage();
  const [walletPage, setWalletPage] = useState(0);

  const customer = useQuery({
    queryKey: ["admin", "customer", id],
    queryFn: async () => unwrap(await api.GET("/api/v1/admin/customers/{id}", { params: { path: { id } } })),
  });
  const wallet = useQuery({
    queryKey: ["admin", "customer", id, "wallet", walletPage],
    enabled: walletPage > 0,
    queryFn: async () =>
      unwrap(await api.GET("/api/v1/admin/wallets/{userId}", { params: { path: { userId: id }, query: { page: walletPage, size: 20 } } })),
  });

  if (customer.isPending) return <Loading />;
  if (customer.isError) {
    const missing = isApiError(customer.error) && customer.error.status === 404;
    return <EmptyState title={missing ? t("notFound") : ta("loadError")} text={missing ? undefined : errorMessage(customer.error)} />;
  }
  const c = customer.data;
  const p = c.profile;
  const tx = (walletPage > 0 ? wallet.data?.transactions : c.wallet?.transactions) ?? c.wallet?.transactions;

  return (
    <>
      <Link href="/admin/customers" className="mb-2 inline-block text-[13px] text-muted hover:text-navy hover:underline">
        ← {t("back")}
      </Link>
      <AdminPageHeader title={p?.phone ?? ""}>
        {p?.status && <span className={`pill ${p.status === "BLOCKED" ? "pill-danger" : ""}`}>{ta(`userStatus.${p.status}`)}</span>}
      </AdminPageHeader>

      <div className="grid grid-cols-1 items-start gap-4 min-[1101px]:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
        <div className="flex min-w-0 flex-col gap-4">
          <Section title={t("profile")}>
            <dl className="m-0 grid grid-cols-2 gap-3 p-4 text-[13px]">
              <div>
                <dt className="text-muted">{t("name")}</dt>
                <dd className="m-0">{p?.fullName || "—"}</dd>
              </div>
              <div>
                <dt className="text-muted">{t("language")}</dt>
                <dd className="m-0">{p?.language}</dd>
              </div>
              <div>
                <dt className="text-muted">{t("roles")}</dt>
                <dd className="m-0">{(p?.roles ?? []).map((r) => tr(r)).join(", ")}</dd>
              </div>
              <div>
                <dt className="text-muted">{t("registered")}</dt>
                <dd className="num m-0">{formatDateTime(p?.createdAt, locale)}</dd>
              </div>
            </dl>
          </Section>

          <Section title={t("orders")}>
            {!c.orders?.content?.length ? (
              <p className="m-0 p-4 text-[13px] text-muted">{t("noOrders")}</p>
            ) : (
              <TableWrap>
                <table className="admin-table">
                  <tbody>
                    {c.orders.content.map((o) => (
                      <tr key={o.id}>
                        <td>
                          {hasRole(session.user, "OPERATOR", "ADMIN") ? (
                            <Link href={`/admin/orders/${o.id}`} className="num font-semibold text-navy hover:underline">
                              {o.number}
                            </Link>
                          ) : (
                            o.number
                          )}
                        </td>
                        <td className="num">{formatDateTime(o.createdAt, locale)}</td>
                        <td>
                          <StatusBadge status={o.status} />
                        </td>
                        <td className="num">{money(o.totalUzs)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </TableWrap>
            )}
          </Section>
        </div>

        <div className="flex min-w-0 flex-col gap-4">
          <Section title={t("wallet")}>
            <div className="border-b border-line px-4 py-3">
              <span className="text-[12px] text-muted">{tw("balance")}</span>
              <b className="num block text-[20px]">{money(c.wallet?.balanceUzs)}</b>
            </div>
            {hasRole(session.user, "ADMIN") && (
              <div className="border-b border-line p-4">
                <WalletAdjustForm userId={id} onDone={() => void customer.refetch()} />
              </div>
            )}
            {!tx?.content?.length ? (
              <p className="m-0 p-4 text-[13px] text-muted">{tw("empty")}</p>
            ) : (
              <TableWrap>
                <table className="admin-table">
                  <tbody>
                    {tx.content.map((w) => (
                      <tr key={w.id}>
                        <td className="num">{formatDateTime(w.createdAt, locale)}</td>
                        <td>{w.type ? tw(`type${w.type}`) : ""}</td>
                        <td className={`num ${(w.amountUzs ?? 0) < 0 ? "" : "text-success"}`}>{money(w.amountUzs)}</td>
                        <td className="whitespace-normal text-muted">{w.comment}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </TableWrap>
            )}
            <Pager page={walletPage} totalPages={tx?.totalPages ?? 0} onChange={setWalletPage} />
          </Section>

          <Section title={t("notifications")}>
            {!c.notifications?.content?.length ? (
              <p className="m-0 p-4 text-[13px] text-muted">{t("noNotifications")}</p>
            ) : (
              <TableWrap>
                <table className="admin-table">
                  <tbody>
                    {c.notifications.content.map((n) => (
                      <tr key={n.id}>
                        <td className="num">{formatDateTime(n.createdAt, locale)}</td>
                        <td>{n.channel}</td>
                        <td>{n.templateKey}</td>
                        <td>
                          <span className={`pill ${n.status === "FAILED" ? "pill-danger" : n.status === "SENT" ? "pill-navy" : ""}`}>
                            {n.status ? tn(`status.${n.status}`) : ""}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </TableWrap>
            )}
          </Section>
        </div>
      </div>
    </>
  );
}

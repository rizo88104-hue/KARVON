"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useLocale, useTranslations } from "next-intl";
import { useState } from "react";
import { api } from "@/api/client";
import { unwrap } from "@/api/errors";
import type { NotificationStatus } from "@/api/types";
import { EmptyState } from "@/components/EmptyState";
import { Link } from "@/i18n/navigation";
import { FORM_QUERY } from "@/lib/admin/roles";
import { useAction } from "@/lib/admin/use-action";
import { formatDateTime } from "@/lib/format";
import { useErrorMessage } from "@/lib/use-error-message";
import { AdminPageHeader, Loading, Pager, Section, TableWrap } from "./ui";

const STATUSES: NotificationStatus[] = ["FAILED", "PENDING", "SENT"];

export function NotificationsView() {
  const t = useTranslations("admin.notifications");
  const ta = useTranslations("admin");
  const locale = useLocale();
  const errorMessage = useErrorMessage();
  const [status, setStatus] = useState<NotificationStatus>("FAILED");
  const [page, setPage] = useState(0);

  const list = useQuery({
    queryKey: ["admin", "notifications", status, page],
    placeholderData: keepPreviousData,
    queryFn: async () =>
      unwrap(
        await api.GET("/api/v1/admin/notifications", {
          params: { query: { status, pageable: { page, size: 30, sort: ["createdAt,desc"] } } },
          querySerializer: FORM_QUERY,
        }),
      ),
  });
  const retry = useAction(
    async (id: string) => unwrap(await api.POST("/api/v1/admin/notifications/{id}/retry", { params: { path: { id } } })),
    { success: (n) => t("retried", { status: n.status ? t(`status.${n.status}`) : "" }), invalidate: [["admin", "notifications"]] },
  );

  return (
    <>
      <AdminPageHeader title={ta("nav.notifications")}>
        <div role="tablist" className="flex gap-1">
          {STATUSES.map((s) => (
            <button
              key={s}
              type="button"
              role="tab"
              aria-selected={status === s}
              className={`chip h-8 ${status === s ? "border-2 border-navy font-bold text-navy" : ""}`}
              onClick={() => (setStatus(s), setPage(0))}
            >
              {t(`status.${s}`)}
            </button>
          ))}
        </div>
      </AdminPageHeader>
      <Section>
        {list.isPending ? (
          <Loading />
        ) : list.isError ? (
          <EmptyState title={ta("loadError")} text={errorMessage(list.error)} />
        ) : !list.data.content?.length ? (
          <p className="m-0 p-4 text-[13px] text-muted">{t("empty")}</p>
        ) : (
          <TableWrap>
            <table className="admin-table">
              <thead>
                <tr>
                  <th>{t("created")}</th>
                  <th>{t("channel")}</th>
                  <th>{t("template")}</th>
                  <th>{t("customer")}</th>
                  <th className="num">{t("attempts")}</th>
                  <th>{t("error")}</th>
                  <th />
                </tr>
              </thead>
              <tbody>
                {list.data.content.map((n) => (
                  <tr key={n.id}>
                    <td className="num">{formatDateTime(n.createdAt, locale)}</td>
                    <td>{n.channel}</td>
                    <td>{n.templateKey}</td>
                    <td>
                      {n.userId && (
                        <Link href={`/admin/customers/${n.userId}`} className="text-navy hover:underline">
                          {t("openCustomer")}
                        </Link>
                      )}
                    </td>
                    <td className="num">{n.attempts}</td>
                    <td className="max-w-[320px] truncate text-danger" title={n.lastError}>
                      {n.lastError}
                    </td>
                    <td className="text-right">
                      {n.status !== "SENT" && (
                        <button
                          type="button"
                          className="btn btn-outline btn-sm"
                          disabled={retry.isPending}
                          onClick={() => n.id && retry.mutate(n.id)}
                        >
                          {t("retry")}
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </TableWrap>
        )}
        <Pager page={page} totalPages={list.data?.totalPages ?? 0} onChange={setPage} />
      </Section>
    </>
  );
}

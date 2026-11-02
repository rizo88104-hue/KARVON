"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useLocale, useTranslations } from "next-intl";
import { Fragment, useState } from "react";
import { api } from "@/api/client";
import { unwrap } from "@/api/errors";
import type { Role, User } from "@/api/types";
import { EmptyState } from "@/components/EmptyState";
import { Modal } from "@/components/Modal";
import { Link } from "@/i18n/navigation";
import { FORM_QUERY } from "@/lib/admin/roles";
import { useAction } from "@/lib/admin/use-action";
import { formatDateTime } from "@/lib/format";
import { useErrorMessage } from "@/lib/use-error-message";
import { useSession } from "@/lib/use-session";
import { ConfirmDialog } from "./ConfirmDialog";
import { AdminPageHeader, isoDate, Loading, Pager, Section, TableWrap } from "./ui";

const ROLES: Role[] = ["CUSTOMER", "OPERATOR", "BUYER", "ADMIN"];

export function StaffView() {
  const t = useTranslations("admin.staff");
  const ta = useTranslations("admin");
  const [tab, setTab] = useState<"users" | "audit">("users");
  return (
    <>
      <AdminPageHeader title={ta("nav.staff")} />
      <div role="tablist" className="card mb-4 flex gap-1 p-1.5">
        {(["users", "audit"] as const).map((k) => (
          <button
            key={k}
            type="button"
            role="tab"
            aria-selected={tab === k}
            onClick={() => setTab(k)}
            className={`rounded-sm border-0 px-4 py-2 text-[14px] ${tab === k ? "bg-orange font-bold text-ink" : "bg-transparent hover:bg-grey-bg"}`}
          >
            {t(`tab.${k}`)}
          </button>
        ))}
      </div>
      {tab === "users" ? <Users /> : <Audit />}
    </>
  );
}

function Users() {
  const t = useTranslations("admin.staff");
  const ta = useTranslations("admin");
  const tr = useTranslations("admin.roles");
  const locale = useLocale();
  const errorMessage = useErrorMessage();
  const session = useSession();
  const [phone, setPhone] = useState("");
  const [role, setRole] = useState<Role | "">("OPERATOR");
  const [status, setStatus] = useState<"" | "ACTIVE" | "BLOCKED">("");
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<User | null>(null);
  const [blocking, setBlocking] = useState<User | null>(null);

  const users = useQuery({
    queryKey: ["admin", "users", phone, role, status, page],
    placeholderData: keepPreviousData,
    queryFn: async () =>
      unwrap(
        await api.GET("/api/v1/admin/users", {
          params: {
            query: {
              phone: phone.replace(/[^\d+]/g, "") || undefined,
              role: role || undefined,
              status: status || undefined,
              pageable: { page, size: 20 },
            },
          },
          querySerializer: FORM_QUERY,
        }),
      ),
  });

  const setUserStatus = useAction(
    async (u: User) =>
      unwrap(
        await api.PATCH("/api/v1/admin/users/{id}/status", {
          params: { path: { id: u.id ?? "" } },
          body: { status: u.status === "BLOCKED" ? "ACTIVE" : "BLOCKED" },
        }),
      ),
    {
      success: (u) => (u.status === "BLOCKED" ? t("blocked") : t("unblocked")),
      invalidate: [["admin", "users"]],
      onSuccess: () => setBlocking(null),
    },
  );

  return (
    <Section>
      <div className="flex flex-wrap items-end gap-2 border-b border-line p-3">
        <label className="label">
          <span className="hint">{t("phone")}</span>
          <input className="input num h-9" inputMode="tel" value={phone} onChange={(e) => (setPhone(e.target.value), setPage(0))} />
        </label>
        <label className="label">
          <span className="hint">{t("role")}</span>
          <select className="input h-9" value={role} onChange={(e) => (setRole(e.target.value as Role | ""), setPage(0))}>
            <option value="">{t("allRoles")}</option>
            {ROLES.map((r) => (
              <option key={r} value={r}>
                {tr(r)}
              </option>
            ))}
          </select>
        </label>
        <label className="label">
          <span className="hint">{t("status")}</span>
          <select className="input h-9" value={status} onChange={(e) => (setStatus(e.target.value as "" | "ACTIVE" | "BLOCKED"), setPage(0))}>
            <option value="">{t("allStatuses")}</option>
            <option value="ACTIVE">{ta("userStatus.ACTIVE")}</option>
            <option value="BLOCKED">{ta("userStatus.BLOCKED")}</option>
          </select>
        </label>
      </div>
      {users.isPending ? (
        <Loading />
      ) : users.isError ? (
        <EmptyState title={ta("loadError")} text={errorMessage(users.error)} />
      ) : !users.data.content?.length ? (
        <p className="m-0 p-4 text-[13px] text-muted">{t("empty")}</p>
      ) : (
        <TableWrap>
          <table className="admin-table">
            <thead>
              <tr>
                <th>{t("phone")}</th>
                <th>{t("name")}</th>
                <th>{t("roles")}</th>
                <th>{t("status")}</th>
                <th>{t("registered")}</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {users.data.content.map((u) => {
                const self = u.id === session.user?.id;
                return (
                  <tr key={u.id}>
                    <td>
                      <Link href={`/admin/customers/${u.id}`} className="num font-semibold text-navy hover:underline">
                        {u.phone}
                      </Link>
                    </td>
                    <td>{u.fullName}</td>
                    <td>
                      <span className="flex flex-wrap gap-1">
                        {(u.roles ?? []).map((r) => (
                          <span key={r} className={`pill ${r === "ADMIN" ? "pill-orange" : r === "CUSTOMER" ? "" : "pill-navy"}`}>
                            {tr(r)}
                          </span>
                        ))}
                      </span>
                    </td>
                    <td>
                      <span className={`pill ${u.status === "BLOCKED" ? "pill-danger" : ""}`}>{u.status ? ta(`userStatus.${u.status}`) : ""}</span>
                    </td>
                    <td className="num">{formatDateTime(u.createdAt, locale)}</td>
                    <td className="text-right">
                      <button type="button" className="link-btn mr-3 text-navy" onClick={() => setEditing(u)}>
                        {t("changeRoles")}
                      </button>
                      {!self && (
                        <button type="button" className={`link-btn ${u.status === "BLOCKED" ? "text-navy" : "text-danger"}`} onClick={() => setBlocking(u)}>
                          {u.status === "BLOCKED" ? t("unblock") : t("block")}
                        </button>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </TableWrap>
      )}
      <Pager page={page} totalPages={users.data?.totalPages ?? 0} onChange={setPage} />

      <RolesDialog user={editing} onClose={() => setEditing(null)} />
      <ConfirmDialog
        open={blocking !== null}
        title={blocking?.status === "BLOCKED" ? t("unblockTitle", { phone: blocking?.phone ?? "" }) : t("blockTitle", { phone: blocking?.phone ?? "" })}
        text={blocking?.status === "BLOCKED" ? undefined : t("blockText")}
        confirmLabel={blocking?.status === "BLOCKED" ? t("unblock") : t("block")}
        busy={setUserStatus.isPending}
        onConfirm={() => blocking && setUserStatus.mutate(blocking)}
        onClose={() => setBlocking(null)}
      />
    </Section>
  );
}

function RolesDialog({ user, onClose }: { user: User | null; onClose: () => void }) {
  const t = useTranslations("admin.staff");
  const tr = useTranslations("admin.roles");
  const [roles, setRoles] = useState<Role[]>([]);
  const [forUser, setForUser] = useState<string | null>(null);
  if (user && user.id !== forUser) {
    setForUser(user.id ?? null);
    setRoles(user.roles ?? []);
  }
  const save = useAction(
    async () => unwrap(await api.PATCH("/api/v1/admin/users/{id}/roles", { params: { path: { id: user?.id ?? "" } }, body: { roles } })),
    { success: t("rolesSaved"), invalidate: [["admin", "users"]], onSuccess: onClose },
  );
  return (
    <Modal open={user !== null} onClose={onClose} title={t("rolesTitle", { phone: user?.phone ?? "" })}>
      <fieldset className="m-0 flex flex-col gap-2 border-0 p-0">
        <legend className="sr-only">{t("roles")}</legend>
        {ROLES.map((r) => (
          <label key={r} className="flex cursor-pointer items-start gap-2 text-[14px]">
            <input
              type="checkbox"
              className="mt-0.5 h-[18px] w-[18px] accent-navy"
              checked={roles.includes(r)}
              onChange={(e) => setRoles((prev) => (e.target.checked ? [...prev, r] : prev.filter((x) => x !== r)))}
            />
            <span>
              <b>{tr(r)}</b>
              <span className="block text-[12px] text-muted">{t(`roleHint.${r}`)}</span>
            </span>
          </label>
        ))}
      </fieldset>
      <button type="button" className="btn btn-orange" disabled={!roles.length || save.isPending} onClick={() => save.mutate(undefined)}>
        {t("saveRoles")}
      </button>
    </Modal>
  );
}

function Audit() {
  const t = useTranslations("admin.staff");
  const ta = useTranslations("admin");
  const locale = useLocale();
  const errorMessage = useErrorMessage();
  const [action, setAction] = useState("");
  const [entityType, setEntityType] = useState("");
  const [from, setFrom] = useState(() => isoDate(new Date(Date.now() - 6 * 86_400_000)));
  const [page, setPage] = useState(0);
  const [open, setOpen] = useState<string | null>(null);

  const audit = useQuery({
    queryKey: ["admin", "audit", action, entityType, from, page],
    placeholderData: keepPreviousData,
    queryFn: async () =>
      unwrap(
        await api.GET("/api/v1/admin/audit", {
          params: {
            query: {
              action: action.trim() || undefined,
              entityType: entityType.trim() || undefined,
              from: from ? new Date(`${from}T00:00:00+05:00`).toISOString() : undefined,
              page,
              size: 50,
            },
          },
        }),
      ),
  });

  return (
    <Section>
      <div className="flex flex-wrap items-end gap-2 border-b border-line p-3">
        <label className="label">
          <span className="hint">{t("since")}</span>
          <input type="date" className="input h-9" value={from} onChange={(e) => (setFrom(e.target.value), setPage(0))} />
        </label>
        <label className="label">
          <span className="hint">{t("action")}</span>
          <input className="input h-9" value={action} onChange={(e) => (setAction(e.target.value), setPage(0))} />
        </label>
        <label className="label">
          <span className="hint">{t("entityType")}</span>
          <input className="input h-9" value={entityType} onChange={(e) => (setEntityType(e.target.value), setPage(0))} />
        </label>
      </div>
      {audit.isPending ? (
        <Loading />
      ) : audit.isError ? (
        <EmptyState title={ta("loadError")} text={errorMessage(audit.error)} />
      ) : !audit.data.content?.length ? (
        <p className="m-0 p-4 text-[13px] text-muted">{t("auditEmpty")}</p>
      ) : (
        <TableWrap>
          <table className="admin-table">
            <thead>
              <tr>
                <th>{t("when")}</th>
                <th>{t("actor")}</th>
                <th>{t("action")}</th>
                <th>{t("entityType")}</th>
                <th>{t("entityId")}</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {audit.data.content.map((e) => (
                <Fragment key={e.id}>
                  <tr>
                    <td className="num">{formatDateTime(e.createdAt, locale)}</td>
                    <td>
                      {e.actorRole}
                      {e.ip && <span className="num block text-[11px] text-muted">{e.ip}</span>}
                    </td>
                    <td className="font-semibold">{e.action}</td>
                    <td>{e.entityType}</td>
                    <td className="num max-w-[200px] truncate">{e.entityId}</td>
                    <td>
                      {(e.before || e.after) && (
                        <button type="button" className="link-btn text-navy" aria-expanded={open === e.id} onClick={() => setOpen(open === e.id ? null : (e.id ?? null))}>
                          {open === e.id ? t("hide") : t("details")}
                        </button>
                      )}
                    </td>
                  </tr>
                  {open === e.id && (
                    <tr>
                      <td colSpan={6} className="bg-[#fafafa]">
                        <div className="grid grid-cols-1 gap-2 whitespace-normal min-[901px]:grid-cols-2">
                          <Json label={t("before")} value={e.before} />
                          <Json label={t("after")} value={e.after} />
                        </div>
                      </td>
                    </tr>
                  )}
                </Fragment>
              ))}
            </tbody>
          </table>
        </TableWrap>
      )}
      <Pager page={page} totalPages={audit.data?.totalPages ?? 0} onChange={setPage} />
    </Section>
  );
}

function Json({ label, value }: { label: string; value?: string }) {
  let pretty = value ?? "—";
  try {
    if (value) pretty = JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    // not JSON: show as is
  }
  return (
    <div className="min-w-0">
      <b className="text-[12px] text-muted">{label}</b>
      <pre className="m-0 max-h-64 overflow-auto rounded-sm border border-line bg-white p-2 text-[11px]">{pretty}</pre>
    </div>
  );
}

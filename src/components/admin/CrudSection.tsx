"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useQuery } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { useMemo, useState, type ReactNode } from "react";
import { Controller, useForm } from "react-hook-form";
import Markdown from "react-markdown";
import { z } from "zod";
import { EmptyState } from "@/components/EmptyState";
import { Modal } from "@/components/Modal";
import { useAction } from "@/lib/admin/use-action";
import { useErrorMessage } from "@/lib/use-error-message";
import { ConfirmDialog } from "./ConfirmDialog";
import { Loading, Pager, Section, TableWrap } from "./ui";

export type FieldDef = {
  name: string;
  label: string;
  type: "text" | "textarea" | "markdown" | "number" | "checkbox" | "datetime" | "select";
  required?: boolean;
  options?: { value: string; label: string }[];
  hint?: string;
  /** Half width on wide screens (two per row). */
  half?: boolean;
};

export type FormValues = Record<string, string | boolean>;

export type ListPage<T> = { rows: T[]; totalPages: number };

type Props<T> = {
  title: string;
  queryKey: unknown[];
  load: (page: number) => Promise<ListPage<T>>;
  rowId: (row: T) => string;
  rowLabel: (row: T) => string;
  headers: string[];
  cells: (row: T) => ReactNode[];
  fields: FieldDef[];
  toForm: (row: T | null) => FormValues;
  save: (values: FormValues, id: string | null) => Promise<unknown>;
  remove: (id: string) => Promise<unknown>;
  filters?: ReactNode;
};

/** List + create/edit dialog + delete confirmation for one kind of content. */
export function CrudSection<T>({ title, queryKey, load, rowId, rowLabel, headers, cells, fields, toForm, save, remove, filters }: Props<T>) {
  const t = useTranslations("admin.crud");
  const ta = useTranslations("admin");
  const errorMessage = useErrorMessage();
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<T | "new" | null>(null);
  const [deleting, setDeleting] = useState<T | null>(null);

  const list = useQuery({ queryKey: [...queryKey, page], queryFn: () => load(page) });
  const del = useAction((id: string) => remove(id), {
    success: t("deleted"),
    invalidate: [queryKey],
    onSuccess: () => setDeleting(null),
  });

  return (
    <Section
      title={title}
      actions={
        <button type="button" className="btn btn-orange btn-sm" onClick={() => setEditing("new")}>
          {t("add")}
        </button>
      }
    >
      {filters && <div className="border-b border-line p-3">{filters}</div>}
      {list.isPending ? (
        <Loading />
      ) : list.isError ? (
        <EmptyState title={ta("loadError")} text={errorMessage(list.error)} />
      ) : list.data.rows.length === 0 ? (
        <p className="m-0 p-4 text-[13px] text-muted">{t("empty")}</p>
      ) : (
        <TableWrap>
          <table className="admin-table">
            <thead>
              <tr>
                {headers.map((h) => (
                  <th key={h}>{h}</th>
                ))}
                <th />
              </tr>
            </thead>
            <tbody>
              {list.data.rows.map((row) => (
                <tr key={rowId(row)}>
                  {cells(row).map((c, i) => (
                    <td key={i}>{c}</td>
                  ))}
                  <td className="text-right">
                    <button type="button" className="link-btn mr-3 text-navy" onClick={() => setEditing(row)}>
                      {t("edit")}
                    </button>
                    <button type="button" className="link-btn text-danger" onClick={() => setDeleting(row)}>
                      {t("delete")}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </TableWrap>
      )}
      <Pager page={page} totalPages={list.data?.totalPages ?? 0} onChange={setPage} />

      <Modal open={editing !== null} onClose={() => setEditing(null)} title={editing === "new" ? t("createTitle", { what: title }) : t("editTitle", { what: title })}>
        {editing !== null && (
          <EntityForm
            fields={fields}
            initial={toForm(editing === "new" ? null : editing)}
            onSubmit={(values) => save(values, editing === "new" ? null : rowId(editing))}
            queryKey={queryKey}
            onDone={() => setEditing(null)}
          />
        )}
      </Modal>
      <ConfirmDialog
        open={deleting !== null}
        title={t("deleteTitle", { name: deleting ? rowLabel(deleting) : "" })}
        text={t("deleteText")}
        confirmLabel={t("delete")}
        busy={del.isPending}
        onConfirm={() => deleting && del.mutate(rowId(deleting))}
        onClose={() => setDeleting(null)}
      />
    </Section>
  );
}

function EntityForm({
  fields,
  initial,
  onSubmit,
  queryKey,
  onDone,
}: {
  fields: FieldDef[];
  initial: FormValues;
  onSubmit: (values: FormValues) => Promise<unknown>;
  queryKey: unknown[];
  onDone: () => void;
}) {
  const t = useTranslations("admin.crud");
  const schema = useMemo(
    () =>
      z.object(
        Object.fromEntries(
          fields.map((f) => {
            if (f.type === "checkbox") return [f.name, z.boolean()];
            let s = z.string();
            if (f.type === "number") {
              s = s.refine((v) => v.trim() === "" || Number.isFinite(Number(v.replace(",", "."))), { message: t("invalidNumber") });
            }
            return [f.name, f.required ? s.refine((v) => v.trim() !== "", { message: t("required") }) : s];
          }),
        ),
      ),
    [fields, t],
  );
  const { register, handleSubmit, formState, control } = useForm<FormValues>({
    // Fields are built at runtime, so the schema type is widened to the form values.
    resolver: zodResolver(schema as unknown as z.ZodType<FormValues, FormValues>),
    defaultValues: initial,
  });
  const submit = useAction((values: FormValues) => onSubmit(values), { success: t("saved"), invalidate: [queryKey], onSuccess: onDone });

  return (
    <form className="grid grid-cols-1 gap-3 min-[561px]:grid-cols-2" onSubmit={handleSubmit((v) => submit.mutate(v))} noValidate>
      {fields.map((f) => {
        const error = formState.errors[f.name]?.message;
        const wide = !f.half || f.type === "textarea" || f.type === "markdown";
        return (
          <div key={f.name} className={wide ? "min-[561px]:col-span-2" : ""}>
            {f.type === "checkbox" ? (
              <label className="flex cursor-pointer items-center gap-2 text-[14px] font-semibold">
                <input type="checkbox" className="h-[18px] w-[18px] accent-navy" {...register(f.name)} />
                {f.label}
              </label>
            ) : f.type === "markdown" ? (
              <Controller
                control={control}
                name={f.name}
                render={({ field }) => <MarkdownEditor label={f.label} value={String(field.value ?? "")} onChange={field.onChange} />}
              />
            ) : (
              <label className="label">
                <span>
                  {f.label}
                  {!f.required && <span className="hint"> ({t("optional")})</span>}
                  {f.hint && <span className="hint"> · {f.hint}</span>}
                </span>
                {f.type === "textarea" ? (
                  <textarea className="input" rows={3} {...register(f.name)} />
                ) : f.type === "select" ? (
                  <select className="input h-9" {...register(f.name)}>
                    {!f.required && <option value="">—</option>}
                    {(f.options ?? []).map((o) => (
                      <option key={o.value} value={o.value}>
                        {o.label}
                      </option>
                    ))}
                  </select>
                ) : (
                  <input
                    className={`input h-9 ${f.type === "number" ? "num" : ""}`}
                    type={f.type === "datetime" ? "datetime-local" : "text"}
                    inputMode={f.type === "number" ? "decimal" : undefined}
                    {...register(f.name)}
                  />
                )}
              </label>
            )}
            {error && <span className="text-[12px] text-danger">{String(error)}</span>}
          </div>
        );
      })}
      <button type="submit" className="btn btn-orange min-[561px]:col-span-2" disabled={submit.isPending}>
        {t("save")}
      </button>
    </form>
  );
}

/** Markdown textarea with a live preview (side by side on wide screens, tabs on phones). */
export function MarkdownEditor({ label, value, onChange }: { label: string; value: string; onChange: (v: string) => void }) {
  const t = useTranslations("admin.crud");
  const [view, setView] = useState<"edit" | "preview">("edit");
  return (
    <div className="flex flex-col gap-1">
      <div className="flex items-center justify-between gap-2">
        <span className="text-[13px] font-semibold">{label}</span>
        <div className="flex gap-1 min-[901px]:hidden">
          {(["edit", "preview"] as const).map((v) => (
            <button key={v} type="button" className="chip h-7 px-2 text-[12px]" aria-pressed={view === v} onClick={() => setView(v)}>
              {t(v === "edit" ? "write" : "preview")}
            </button>
          ))}
        </div>
      </div>
      <div className="grid grid-cols-1 gap-2 min-[901px]:grid-cols-2">
        <textarea
          className={`input min-h-[200px] font-mono text-[13px] ${view === "edit" ? "" : "hidden min-[901px]:block"}`}
          aria-label={label}
          value={value}
          onChange={(e) => onChange(e.target.value)}
        />
        <div
          className={`prose-page min-h-[200px] overflow-auto rounded-sm border border-line bg-[#fafafa] p-3 text-[14px] ${
            view === "preview" ? "" : "hidden min-[901px]:block"
          }`}
          aria-label={t("preview")}
        >
          <Markdown>{value}</Markdown>
        </div>
      </div>
    </div>
  );
}

/** "2026-11-02T10:00" (datetime-local) <-> ISO instant. */
export function toLocalInput(iso?: string | null) {
  if (!iso) return "";
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

export function fromLocalInput(v: string | boolean | undefined) {
  return typeof v === "string" && v ? new Date(v).toISOString() : undefined;
}

export function str(v: string | boolean | undefined) {
  return typeof v === "string" && v.trim() ? v.trim() : undefined;
}

export function num(v: string | boolean | undefined) {
  return typeof v === "string" && v.trim() ? Number(v.replace(",", ".")) : undefined;
}

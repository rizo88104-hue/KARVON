"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useTranslations } from "next-intl";
import { useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { api } from "@/api/client";
import { unwrap } from "@/api/errors";
import type { Address, AddressRequest } from "@/api/types";
import { EmptyState } from "@/components/EmptyState";
import { Modal } from "@/components/Modal";
import { useToast } from "@/components/Toast";
import { keys, useAddresses } from "@/lib/queries";
import { useErrorMessage } from "@/lib/use-error-message";

const FIELDS = ["label", "region", "city", "district", "street", "house", "apartment", "landmark", "phone"] as const;
const REQUIRED = new Set<(typeof FIELDS)[number]>(["region", "city", "street", "house"]);

export function AddressesView() {
  const t = useTranslations("addresses");
  const tc = useTranslations("common");
  const toast = useToast();
  const errorMessage = useErrorMessage();
  const qc = useQueryClient();
  const addresses = useAddresses();
  const [editing, setEditing] = useState<Address | "new" | null>(null);
  const [deleting, setDeleting] = useState<Address | null>(null);

  const invalidate = () => qc.invalidateQueries({ queryKey: keys.addresses });
  const remove = useMutation({
    mutationFn: async (id: string) => {
      const res = await api.DELETE("/api/v1/me/addresses/{id}", { params: { path: { id } } });
      if (!res.response.ok) unwrap(res);
    },
    onSuccess: () => {
      setDeleting(null);
      toast(t("deleted"));
      return invalidate();
    },
    onError: (e) => toast(errorMessage(e)),
  });
  const makeDefault = useMutation({
    mutationFn: async (a: Address) =>
      unwrap(await api.PUT("/api/v1/me/addresses/{id}", { params: { path: { id: a.id ?? "" } }, body: toRequest(a, true) })),
    onSuccess: () => invalidate(),
    onError: (e) => toast(errorMessage(e)),
  });

  if (addresses.isPending) {
    return (
      <p className="py-14 text-center text-muted" role="status">
        {tc("loading")}
      </p>
    );
  }
  if (addresses.isError) return <EmptyState title={t("error")} text={errorMessage(addresses.error)} />;

  return (
    <>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <h1 className="m-0 text-[24px] font-bold">{t("title")}</h1>
        <button type="button" className="btn btn-orange" onClick={() => setEditing("new")}>
          {t("add")}
        </button>
      </div>
      {addresses.data.length === 0 ? (
        <EmptyState title={t("empty")} />
      ) : (
        <ul className="m-0 grid list-none grid-cols-1 gap-3 p-0 min-[901px]:grid-cols-2">
          {addresses.data.map((a) => (
            <li key={a.id} className="card flex flex-col gap-2 p-4 text-[14px]">
              <div className="flex items-center gap-2">
                <b className="text-[15px]">{a.label || a.city}</b>
                {a.isDefault && <span className="rounded-[6px] bg-navy px-2 py-0.5 text-[12px] font-bold text-white">{t("default")}</span>}
              </div>
              <span>{[a.region, a.city, a.district, a.street, a.house, a.apartment].filter(Boolean).join(", ")}</span>
              {a.landmark && <span className="text-muted">{a.landmark}</span>}
              {a.phone && <span className="num text-muted">{a.phone}</span>}
              <div className="mt-auto flex flex-wrap gap-x-4 gap-y-2 pt-2">
                <button type="button" className="link-btn" onClick={() => setEditing(a)}>
                  {tc("edit")}
                </button>
                {!a.isDefault && (
                  <button type="button" className="link-btn" disabled={makeDefault.isPending} onClick={() => makeDefault.mutate(a)}>
                    {t("makeDefault")}
                  </button>
                )}
                <button type="button" className="link-btn text-danger" onClick={() => setDeleting(a)}>
                  {tc("delete")}
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}

      <Modal open={editing !== null} onClose={() => setEditing(null)} title={editing === "new" ? t("addTitle") : t("editTitle")}>
        {editing !== null && (
          <AddressForm
            address={editing === "new" ? null : editing}
            onSaved={() => {
              setEditing(null);
              toast(t("saved"));
              void invalidate();
            }}
          />
        )}
      </Modal>
      <Modal open={deleting !== null} onClose={() => setDeleting(null)} title={t("deleteConfirm")}>
        <div className="flex gap-2">
          <button type="button" className="btn btn-outline" onClick={() => setDeleting(null)}>
            {tc("cancel")}
          </button>
          <button
            type="button"
            className="btn btn-navy flex-1"
            disabled={remove.isPending}
            onClick={() => deleting?.id && remove.mutate(deleting.id)}
          >
            {tc("delete")}
          </button>
        </div>
      </Modal>
    </>
  );
}

function toRequest(a: Address, isDefault = !!a.isDefault): AddressRequest {
  return {
    label: a.label,
    region: a.region ?? "",
    city: a.city ?? "",
    district: a.district,
    street: a.street ?? "",
    house: a.house ?? "",
    apartment: a.apartment,
    landmark: a.landmark,
    phone: a.phone,
    isDefault,
  };
}

function AddressForm({ address, onSaved }: { address: Address | null; onSaved: () => void }) {
  const t = useTranslations("addresses");
  const tc = useTranslations("common");
  const errorMessage = useErrorMessage();

  const schema = useMemo(() => {
    const required = z.string().trim().min(1, t("required")).max(200, t("tooLong"));
    const optional = z.string().trim().max(200, t("tooLong")).optional();
    return z.object({
      label: optional,
      region: required,
      city: required,
      district: optional,
      street: required,
      house: required,
      apartment: optional,
      landmark: optional,
      phone: optional,
      isDefault: z.boolean(),
    });
  }, [t]);
  type Values = z.infer<typeof schema>;

  const { register, handleSubmit, formState, setError } = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: {
      label: address?.label ?? "",
      region: address?.region ?? "",
      city: address?.city ?? "",
      district: address?.district ?? "",
      street: address?.street ?? "",
      house: address?.house ?? "",
      apartment: address?.apartment ?? "",
      landmark: address?.landmark ?? "",
      phone: address?.phone ?? "",
      isDefault: address?.isDefault ?? false,
    },
  });

  const onSubmit = handleSubmit(async (values) => {
    const body: AddressRequest = Object.fromEntries(
      Object.entries(values).map(([k, v]) => [k, typeof v === "string" ? v.trim() || undefined : v]),
    ) as AddressRequest;
    try {
      if (address?.id) {
        unwrap(await api.PUT("/api/v1/me/addresses/{id}", { params: { path: { id: address.id } }, body }));
      } else {
        unwrap(await api.POST("/api/v1/me/addresses", { body }));
      }
      onSaved();
    } catch (e) {
      setError("root", { message: errorMessage(e) });
    }
  });

  return (
    <form onSubmit={onSubmit} noValidate className="grid grid-cols-1 gap-3 min-[561px]:grid-cols-2">
      {FIELDS.map((name) => (
        <label key={name} className={`flex flex-col gap-1 text-[14px] ${name === "street" || name === "landmark" ? "min-[561px]:col-span-2" : ""}`}>
          <span className="font-semibold">
            {t(name)}
            {!REQUIRED.has(name) && <span className="font-normal text-muted"> ({tc("optional")})</span>}
          </span>
          <input
            className="input"
            type={name === "phone" ? "tel" : "text"}
            placeholder={name === "label" ? t("labelPlaceholder") : undefined}
            aria-invalid={!!formState.errors[name]}
            {...register(name)}
          />
          {formState.errors[name] && <span className="text-[13px] text-danger">{formState.errors[name]?.message}</span>}
        </label>
      ))}
      <label className="flex cursor-pointer items-center gap-2 text-[14px] min-[561px]:col-span-2">
        <input type="checkbox" className="h-[18px] w-[18px] accent-navy" {...register("isDefault")} />
        {t("makeDefault")}
      </label>
      {formState.errors.root && <p className="m-0 text-[13px] text-danger min-[561px]:col-span-2">{formState.errors.root.message}</p>}
      <button type="submit" className="btn btn-orange min-[561px]:col-span-2" disabled={formState.isSubmitting}>
        {tc("save")}
      </button>
    </form>
  );
}

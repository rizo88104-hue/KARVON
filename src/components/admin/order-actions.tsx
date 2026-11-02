"use client";

import Image from "next/image";
import { useTranslations } from "next-intl";
import { useRef, useState } from "react";
import { api } from "@/api/client";
import { unwrap } from "@/api/errors";
import type { OrderItem, StaffOrder } from "@/api/types";
import { useToast } from "@/components/Toast";
import { useAction } from "@/lib/admin/use-action";
import { ConfirmDialog } from "./ConfirmDialog";

/** Query keys refreshed after any change to one order. */
export function orderKeys(id: string) {
  return [["admin", "order", id], ["admin", "orders"], ["admin", "queue"], ["admin", "dashboard"], ["admin", "delayed"]];
}

const MAX_PHOTOS = 4;
const MAX_BYTES = 5 * 1024 * 1024;

/** Parcel photos (JPEG/PNG, up to 4, 5 MB each): upload and delete with confirmation. Shown to the customer. */
export function PhotoManager({ order }: { order: StaffOrder }) {
  const t = useTranslations("admin.orderActions");
  const toast = useToast();
  const input = useRef<HTMLInputElement>(null);
  const [deleting, setDeleting] = useState<string | null>(null);
  const id = order.order?.id ?? "";
  const photos = order.order?.photos ?? [];

  const upload = useAction(
    async (files: File[]) => {
      const form = new FormData();
      files.forEach((f) => form.append("files", f));
      return unwrap(
        await api.POST("/api/v1/admin/orders/{id}/photos", {
          params: { path: { id } },
          body: { files: [] },
          bodySerializer: () => form,
        }),
      );
    },
    { success: t("photosUploaded"), invalidate: orderKeys(id) },
  );
  const remove = useAction(
    async (photoId: string) =>
      unwrap(await api.DELETE("/api/v1/admin/orders/{id}/photos/{photoId}", { params: { path: { id, photoId } } })),
    { success: t("photoDeleted"), invalidate: orderKeys(id), onSuccess: () => setDeleting(null) },
  );

  return (
    <div className="flex flex-col gap-2">
      {photos.length > 0 && (
        <div className="grid grid-cols-4 gap-2">
          {photos.map((p, i) => (
            <div key={p.id} className="relative aspect-square overflow-hidden rounded-sm bg-grey-bg">
              {p.url && <Image src={p.url} alt={t("photo", { index: i + 1 })} fill sizes="120px" className="object-cover" />}
              <button
                type="button"
                className="absolute top-1 right-1 rounded-[6px] border-0 bg-ink/80 px-1.5 text-[12px] text-white"
                aria-label={t("deletePhoto", { index: i + 1 })}
                onClick={() => p.id && setDeleting(p.id)}
              >
                ✕
              </button>
            </div>
          ))}
        </div>
      )}
      <input
        ref={input}
        type="file"
        accept="image/jpeg,image/png"
        multiple
        capture="environment"
        className="hidden"
        onChange={(e) => {
          const files = [...(e.target.files ?? [])];
          e.target.value = "";
          if (!files.length) return;
          if (files.length + photos.length > MAX_PHOTOS) return toast(t("photoLimit", { max: MAX_PHOTOS }));
          if (files.some((f) => f.size > MAX_BYTES)) return toast(t("photoTooBig"));
          upload.mutate(files);
        }}
      />
      <button
        type="button"
        className="btn btn-outline btn-sm self-start"
        disabled={upload.isPending || photos.length >= MAX_PHOTOS}
        onClick={() => input.current?.click()}
      >
        {upload.isPending ? t("uploading") : t("addPhotos")}
      </button>
      <ConfirmDialog
        open={deleting !== null}
        title={t("deletePhotoTitle")}
        confirmLabel={t("delete")}
        busy={remove.isPending}
        onConfirm={() => deleting && remove.mutate(deleting)}
        onClose={() => setDeleting(null)}
      />
    </div>
  );
}

/** PURCHASED -> AT_WAREHOUSE with the real weight (a heavier parcel makes the customer pay the difference). */
export function WeightForm({ order }: { order: StaffOrder }) {
  const t = useTranslations("admin.orderActions");
  const id = order.order?.id ?? "";
  const [weight, setWeight] = useState("");
  const [comment, setComment] = useState("");
  const save = useAction(
    async () =>
      unwrap(
        await api.POST("/api/v1/admin/orders/{id}/weight", {
          params: { path: { id } },
          body: { realWeightKg: Number(weight.replace(",", ".")), comment: comment.trim() || undefined },
        }),
      ),
    { success: t("weightSaved"), invalidate: orderKeys(id) },
  );
  const valid = Number(weight.replace(",", ".")) > 0;
  return (
    <form
      className="flex flex-col gap-3"
      onSubmit={(e) => {
        e.preventDefault();
        if (valid) save.mutate(undefined);
      }}
    >
      <div className="grid grid-cols-1 gap-3 min-[561px]:grid-cols-[160px_1fr]">
        <label className="label">
          {t("weightKg")}
          <input className="input num" inputMode="decimal" value={weight} onChange={(e) => setWeight(e.target.value)} required />
        </label>
        <label className="label">
          <span>
            {t("comment")} <span className="hint">({t("optional")})</span>
          </span>
          <input className="input" value={comment} onChange={(e) => setComment(e.target.value)} />
        </label>
      </div>
      <PhotoManager order={order} />
      <button type="submit" className="btn btn-orange self-start" disabled={!valid || save.isPending}>
        {t("saveWeight")}
      </button>
    </form>
  );
}

/** The buyer bought one item on Taobao: actual price, Taobao order number, China tracking number. */
export function PurchaseItemForm({ orderId, item }: { orderId: string; item: { id: string; priceCny?: number } }) {
  const t = useTranslations("admin.orderActions");
  const [price, setPrice] = useState(item.priceCny != null ? String(item.priceCny) : "");
  const [taobaoOrder, setTaobaoOrder] = useState("");
  const [tracking, setTracking] = useState("");
  const [outOfStock, setOutOfStock] = useState(false);
  const [reason, setReason] = useState("");

  const purchased = useAction(
    async () =>
      unwrap(
        await api.POST("/api/v1/admin/procurement/items/{itemId}/purchased", {
          params: { path: { itemId: item.id } },
          body: {
            actualPriceCny: Number(price.replace(",", ".")),
            taobaoOrderNumber: taobaoOrder.trim() || undefined,
            chinaTrackingNumber: tracking.trim() || undefined,
          },
        }),
      ),
    { success: t("itemPurchased"), invalidate: orderKeys(orderId) },
  );
  const missing = useAction(
    async () =>
      unwrap(
        await api.POST("/api/v1/admin/procurement/items/{itemId}/out-of-stock", {
          params: { path: { itemId: item.id } },
          body: { comment: reason.trim() || undefined },
        }),
      ),
    { success: t("itemOutOfStock"), invalidate: orderKeys(orderId), onSuccess: () => setOutOfStock(false) },
  );
  const valid = Number(price.replace(",", ".")) > 0;

  return (
    <form
      className="grid grid-cols-1 gap-2 rounded-sm bg-grey-bg/60 p-3 min-[561px]:grid-cols-3"
      onSubmit={(e) => {
        e.preventDefault();
        if (valid) purchased.mutate(undefined);
      }}
    >
      <label className="label">
        {t("actualPriceCny")}
        <input className="input num h-9" inputMode="decimal" value={price} onChange={(e) => setPrice(e.target.value)} required />
      </label>
      <label className="label">
        {t("taobaoOrderNumber")}
        <input className="input num h-9" value={taobaoOrder} onChange={(e) => setTaobaoOrder(e.target.value)} />
      </label>
      <label className="label">
        {t("chinaTracking")}
        <input className="input num h-9" value={tracking} onChange={(e) => setTracking(e.target.value)} />
      </label>
      <div className="flex flex-wrap gap-2 min-[561px]:col-span-3">
        <button type="submit" className="btn btn-orange btn-sm" disabled={!valid || purchased.isPending}>
          {t("markPurchased")}
        </button>
        <button type="button" className="btn btn-outline btn-sm" onClick={() => setOutOfStock(true)}>
          {t("markOutOfStock")}
        </button>
      </div>
      <ConfirmDialog
        open={outOfStock}
        title={t("outOfStockTitle")}
        text={t("outOfStockText")}
        confirmLabel={t("markOutOfStock")}
        busy={missing.isPending}
        onConfirm={() => missing.mutate(undefined)}
        onClose={() => setOutOfStock(false)}
      >
        <label className="label">
          {t("comment")}
          <input className="input" value={reason} onChange={(e) => setReason(e.target.value)} />
        </label>
      </ConfirmDialog>
    </form>
  );
}

/** Taobao product page for an item. */
export function taobaoUrl(item: Pick<OrderItem, "externalProductId">) {
  return `https://item.taobao.com/item.htm?id=${encodeURIComponent(item.externalProductId ?? "")}`;
}

/** Copies text and confirms with a toast. */
export function CopyButton({ text, label }: { text: string; label: string }) {
  const t = useTranslations("admin.orderActions");
  const toast = useToast();
  return (
    <button
      type="button"
      className="btn btn-outline btn-sm"
      onClick={async () => {
        try {
          await navigator.clipboard.writeText(text);
          toast(t("copied"));
        } catch {
          toast(t("copyFailed"));
        }
      }}
    >
      {label}
    </button>
  );
}

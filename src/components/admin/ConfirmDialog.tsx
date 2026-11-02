"use client";

import { useTranslations } from "next-intl";
import type { ReactNode } from "react";
import { Modal } from "@/components/Modal";

/** In-page confirmation for destructive actions (never the browser's confirm()). */
export function ConfirmDialog({
  open,
  title,
  text,
  confirmLabel,
  busy,
  disabled,
  onConfirm,
  onClose,
  children,
}: {
  open: boolean;
  title: string;
  text?: string;
  confirmLabel: string;
  busy?: boolean;
  disabled?: boolean;
  onConfirm: () => void;
  onClose: () => void;
  children?: ReactNode;
}) {
  const t = useTranslations("common");
  return (
    <Modal open={open} onClose={onClose} title={title}>
      {text && <p className="m-0 text-subtle">{text}</p>}
      {children}
      <div className="flex flex-col-reverse gap-2 min-[561px]:flex-row min-[561px]:justify-end">
        <button type="button" className="btn btn-outline" onClick={onClose}>
          {t("cancel")}
        </button>
        <button type="button" className="btn btn-danger" disabled={busy || disabled} onClick={onConfirm}>
          {confirmLabel}
        </button>
      </div>
    </Modal>
  );
}

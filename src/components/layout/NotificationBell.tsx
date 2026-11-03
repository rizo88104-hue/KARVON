"use client";

import { useLocale, useTranslations } from "next-intl";
import { type CSSProperties, useEffect, useId, useRef, useState } from "react";
import type { InAppNotification } from "@/api/types";
import { formatDateTime } from "@/lib/format";
import { useInbox, useMarkRead, useUnreadCount } from "@/lib/queries";

/**
 * Bell with the unread count and a dropdown of the in-app notifications (what Telegram could not deliver).
 * Shown only to signed-in users. Opening it loads the list; a message is marked read when it is clicked.
 */
export function NotificationBell() {
  const t = useTranslations("inbox");
  const tc = useTranslations("common");
  const locale = useLocale();
  const [open, setOpen] = useState(false);
  // Phones: the panel spans the screen width just below the bell (measured when it opens).
  const [top, setTop] = useState(0);
  const panelId = useId();
  const root = useRef<HTMLDivElement>(null);
  const unread = useUnreadCount();
  const inbox = useInbox(locale, open);
  const markRead = useMarkRead();
  const count = unread.data?.count ?? 0;

  useEffect(() => {
    if (!open) return;
    function onPointer(e: PointerEvent) {
      if (root.current && !root.current.contains(e.target as Node)) setOpen(false);
    }
    function onKey(e: KeyboardEvent) {
      if (e.key === "Escape") setOpen(false);
    }
    document.addEventListener("pointerdown", onPointer);
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("pointerdown", onPointer);
      document.removeEventListener("keydown", onKey);
    };
  }, [open]);

  const items = inbox.data?.content ?? [];

  return (
    <div ref={root} className="relative">
      <button
        type="button"
        className="btn btn-ghost relative h-10 px-2.5"
        aria-label={count ? t("openWithCount", { count }) : t("open")}
        aria-expanded={open}
        aria-controls={panelId}
        onClick={() => {
          setTop((root.current?.getBoundingClientRect().bottom ?? 56) + 8);
          setOpen((v) => !v);
        }}
      >
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
          <path d="M6 8a6 6 0 1 1 12 0c0 7 3 9 3 9H3s3-2 3-9" />
          <path d="M10.3 21a1.94 1.94 0 0 0 3.4 0" />
        </svg>
        {count > 0 && (
          <span className="num absolute top-0.5 left-6 min-w-[18px] rounded-full bg-orange px-1 text-center text-[11px] leading-[18px] font-bold text-ink">
            {count > 99 ? "99+" : count}
          </span>
        )}
      </button>
      {open && (
        <div
          id={panelId}
          role="dialog"
          aria-label={t("title")}
          style={{ "--bell-top": `${top}px` } as CSSProperties}
          className="card fixed top-[var(--bell-top)] right-4 left-4 z-50 flex max-h-[70vh] flex-col overflow-hidden p-0 shadow-lg min-[561px]:absolute min-[561px]:top-12 min-[561px]:right-0 min-[561px]:left-auto min-[561px]:w-[360px]"
        >
          <div className="flex items-center justify-between gap-2 border-b border-line px-4 py-3">
            <h2 className="m-0 text-[16px] font-bold">{t("title")}</h2>
            {count > 0 && (
              <button
                type="button"
                className="link-btn text-[13px]"
                disabled={markRead.isPending}
                onClick={() => markRead.mutate(undefined)}
              >
                {t("markAllRead")}
              </button>
            )}
          </div>
          <div className="overflow-y-auto">
            {inbox.isPending ? (
              <p className="m-0 px-4 py-6 text-center text-[14px] text-muted" role="status">
                {tc("loading")}
              </p>
            ) : inbox.isError ? (
              <p className="m-0 px-4 py-6 text-center text-[14px] text-danger">{t("error")}</p>
            ) : items.length === 0 ? (
              <p className="m-0 px-4 py-6 text-center text-[14px] text-muted">{t("empty")}</p>
            ) : (
              <ul className="m-0 list-none p-0">
                {items.map((item) => (
                  <InboxItem
                    key={item.id}
                    item={item}
                    locale={locale}
                    unreadLabel={t("unread")}
                    onRead={() => item.id && !item.read && markRead.mutate(item.id)}
                  />
                ))}
              </ul>
            )}
          </div>
        </div>
      )}
    </div>
  );
}

function InboxItem({
  item,
  locale,
  unreadLabel,
  onRead,
}: {
  item: InAppNotification;
  locale: string;
  unreadLabel: string;
  onRead: () => void;
}) {
  return (
    <li className="border-b border-line last:border-b-0">
      <button
        type="button"
        className={`flex w-full flex-col gap-1 px-4 py-3 text-left text-[14px] hover:bg-grey-bg ${item.read ? "" : "bg-orange/10"}`}
        onClick={onRead}
      >
        <span className="flex items-start gap-2">
          {!item.read && (
            <span className="mt-1.5 h-2 w-2 shrink-0 rounded-full bg-orange" aria-label={unreadLabel} role="img" />
          )}
          <span className={item.read ? "text-subtle" : "font-semibold"}>{item.text}</span>
        </span>
        <span className="num text-[12px] text-muted">{formatDateTime(item.createdAt, locale)}</span>
      </button>
    </li>
  );
}

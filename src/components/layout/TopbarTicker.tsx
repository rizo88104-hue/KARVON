import type { TickerItem } from "@/api/types";
import { Link } from "@/i18n/navigation";

/**
 * Running line in the top bar (messages from the admin panel, GET /api/v1/ticker). The list is drawn twice and the
 * track moves by half its width, so it loops without a gap (.ticker in globals.css). It stops on hover or focus;
 * the second copy is inert, so keyboard and screen readers meet every message once. Without messages: `fallback`.
 */
export function TopbarTicker({ items, label, fallback }: { items: TickerItem[]; label: string; fallback: string }) {
  if (items.length === 0) return <span className="min-w-0 truncate">{fallback}</span>;

  // About 0.2 s per character keeps the speed the same however long the messages are.
  const length = items.reduce((sum, m) => sum + (m.text?.length ?? 0), 0);
  const duration = `${Math.max(20, Math.round(length * 0.2))}s`;

  const list = (copy: boolean) => (
    <ul className="ticker-list" aria-hidden={copy || undefined} inert={copy || undefined}>
      {items.map((m, i) => (
        <li key={i}>
          {!m.linkUrl ? (
            m.text
          ) : m.linkUrl.startsWith("/") ? (
            <Link href={m.linkUrl} className="hover:underline">
              {m.text}
            </Link>
          ) : (
            <a href={m.linkUrl} rel="noopener" className="hover:underline">
              {m.text}
            </a>
          )}
        </li>
      ))}
    </ul>
  );

  return (
    <section className="ticker min-w-0 flex-1" aria-label={label}>
      <div className="ticker-track" style={{ "--ticker-duration": duration } as React.CSSProperties}>
        {list(false)}
        {list(true)}
      </div>
    </section>
  );
}

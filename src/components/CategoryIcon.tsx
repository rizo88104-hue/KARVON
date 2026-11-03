import type { Category } from "@/api/types";

// Simple line icons (24x24, stroke) for the top categories. A category's `icon` (set by ADMIN) may name one of these
// keys; otherwise the slug is used, and unknown categories get the tag icon.
const ICONS: Record<string, React.ReactNode> = {
  kiyim: <path d="M8 3 4 6l2 4 2-1v12h8V9l2 1 2-4-4-3c-.5 1.7-2 3-4 3S8.5 4.7 8 3z" />,
  poyabzal: <path d="M3 16V8l4 1 3 3.5 8.5 2A2.5 2.5 0 0 1 21 17v1H3zM3 18h18M7 9v2.5M10 12.5l-1.5 1.5" />,
  sumkalar: <path d="M5 8h14l-1.2 12H6.2L5 8zM9 8V6.5a3 3 0 0 1 6 0V8" />,
  elektronika: <path d="M4 15v-3a8 8 0 0 1 16 0v3M4 15h3v5H5a1 1 0 0 1-1-1v-4zM20 15h-3v5h2a1 1 0 0 0 1-1v-4z" />,
  "uy-rozgor": <path d="M3 11 12 4l9 7M5 9.5V20h14V9.5M10 20v-6h4v6" />,
  bolalar: <path d="M4 13h7v7H4zM13 13h7v7h-7zM8.5 4h7v7h-7z" />,
  aksessuarlar: (
    <>
      <circle cx="12" cy="12" r="5" />
      <path d="M12 10v2l1.5 1.5M9 7.5 10 3h4l1 4.5M9 16.5l1 4.5h4l1-4.5" />
    </>
  ),
  gozallik: (
    <path d="M12 3l1.8 4.7 4.7 1.8-4.7 1.8L12 16l-1.8-4.7-4.7-1.8 4.7-1.8L12 3zM18.5 14.5l.8 2.2 2.2.8-2.2.8-.8 2.2-.8-2.2-2.2-.8 2.2-.8z" />
  ),
  "oziq-ovqat": (
    <path d="M12 7.5c-1.6-1.1-4.2-1.3-5.7.3-2 2-1.6 6 .1 9 1.2 2.2 2.9 3.9 4.5 3.5.4-.1.7-.3 1.1-.3s.7.2 1.1.3c1.6.4 3.3-1.3 4.5-3.5 1.7-3 2.1-7 .1-9-1.5-1.6-4.1-1.4-5.7-.3zM12 7.5c0-2.2 1-3.7 3-4.5" />
  ),
  sport: <path d="M6.5 6.5v11M17.5 6.5v11M3.5 9v6M20.5 9v6M6.5 12h11" />,
  tag: <path d="M3 12V4h8l10 10-8 8L3 12zM7.5 8.5h.01" />,
};

export function CategoryIcon({ category, size = 20, className }: { category: Category; size?: number; className?: string }) {
  const key = [category.icon, category.slug].find((k) => k && k in ICONS) ?? "tag";
  return (
    <svg
      viewBox="0 0 24 24"
      width={size}
      height={size}
      fill="none"
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      className={className}
    >
      {ICONS[key]}
    </svg>
  );
}

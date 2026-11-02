import type { ReactNode } from "react";
import "./globals.css";

// The <html> element is rendered by app/[locale]/layout.tsx, which knows the language.
export default function RootLayout({ children }: { children: ReactNode }) {
  return children;
}

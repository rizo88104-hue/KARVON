import Link from "next/link";
import uz from "@/messages/uz.json";
import "./globals.css";

// Requests outside the locale routes (rare: the proxy sends almost everything to /[locale]).
export default function GlobalNotFound() {
  return (
    <html lang="uz">
      <body className="grid min-h-dvh place-items-center p-4 text-center">
        <div className="flex flex-col items-center gap-3">
          <Link href="/" className="text-[28px] font-bold text-navy">
            E-Market
          </Link>
          <h1 className="m-0 text-[22px] font-bold">{uz.errors.notFoundTitle}</h1>
          <p className="m-0 text-muted">{uz.errors.notFoundText}</p>
          <Link href="/" className="btn btn-orange">
            {uz.errors.goHome}
          </Link>
        </div>
      </body>
    </html>
  );
}

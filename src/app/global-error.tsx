"use client";

import uz from "@/messages/uz.json";
import "./globals.css";

// Last-resort error page when the root layout itself fails (no translations provider available).
export default function GlobalError({ reset }: { error: Error & { digest?: string }; reset: () => void }) {
  return (
    <html lang="uz">
      <body className="grid min-h-dvh place-items-center p-4 text-center">
        <div className="flex flex-col items-center gap-3">
          <h1 className="m-0 text-[22px] font-bold">{uz.errors.errorTitle}</h1>
          <p className="m-0 text-muted">{uz.errors.errorText}</p>
          <button type="button" className="btn btn-orange" onClick={reset}>
            {uz.common.retry}
          </button>
        </div>
      </body>
    </html>
  );
}

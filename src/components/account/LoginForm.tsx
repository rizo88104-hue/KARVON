"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useTranslations } from "next-intl";
import { useSearchParams } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { api } from "@/api/client";
import { ApiError, unwrap } from "@/api/errors";
import type { OtpChannel, Problem } from "@/api/types";
import { useToast } from "@/components/Toast";
import { useRouter } from "@/i18n/navigation";
import { verifyOtp } from "@/lib/session";
import { normalizePhone } from "@/lib/site";
import { useErrorMessage } from "@/lib/use-error-message";
import { useSession } from "@/lib/use-session";

/** Only same-site paths, never "//evil.com". */
function safeNext(value: string | null) {
  return value && value.startsWith("/") && !value.startsWith("//") ? value : "/account/orders";
}

export function LoginForm() {
  const t = useTranslations("login");
  const toast = useToast();
  const errorMessage = useErrorMessage();
  const router = useRouter();
  const next = safeNext(useSearchParams().get("next"));
  const session = useSession();
  const [sent, setSent] = useState<{ phone: string; channel: OtpChannel; link?: string } | null>(null);
  const [resendAt, setResendAt] = useState(0);

  useEffect(() => {
    if (session.status === "authenticated") router.replace(next);
  }, [session.status, next, router]);

  async function requestCode(normalized: string) {
    const res = unwrap(await api.POST("/api/v1/auth/otp/request", { body: { phone: normalized } }));
    setSent({ phone: normalized, channel: res.channel, link: res.link });
    setResendAt(Date.now() + Math.min(res.expiresInSeconds ?? 60, 120) * 1000);
  }

  const subtitle = !sent
    ? t("subtitle")
    : sent.channel === "TELEGRAM"
      ? t("codeSentTelegram")
      : sent.channel === "SMS"
        ? t("codeSent", { phone: sent.phone })
        : t("linkTitle");

  return (
    <div className="card mx-auto flex w-full max-w-[420px] flex-col gap-4 p-5 min-[561px]:p-7">
      <div>
        <h1 className="m-0 text-[24px] font-bold">{t("title")}</h1>
        <p className="m-0 mt-1 text-muted">{subtitle}</p>
        {sent?.channel === "TELEGRAM" && <p className="m-0 mt-1 text-[14px] text-subtle">{t("codeSentTelegramHint")}</p>}
      </div>
      {sent?.channel === "TELEGRAM_LINK" && sent.link && <TelegramLinkStep link={sent.link} />}
      {sent ? (
        <CodeStep
          phone={sent.phone}
          resendAt={resendAt}
          onChangePhone={() => setSent(null)}
          onResend={() => requestCode(sent.phone).catch((e) => toast(errorMessage(e)))}
          onDone={() => {
            toast(t("welcome"));
            router.replace(next);
          }}
        />
      ) : (
        <PhoneStep onSubmit={requestCode} />
      )}
    </div>
  );
}

/** No Telegram chat is linked to the phone yet: open the bot, press Start and share the number; the bot sends the code. */
function TelegramLinkStep({ link }: { link: string }) {
  const t = useTranslations("login");
  return (
    <div className="flex flex-col gap-3">
      <a href={link} target="_blank" rel="noopener noreferrer" className="btn btn-orange h-12 gap-2 text-[16px]">
        <svg viewBox="0 0 24 24" width="22" height="22" fill="currentColor" aria-hidden="true">
          <path d="M21.9 4.3 18.7 19.4c-.2 1-.9 1.3-1.8.8l-4.9-3.6-2.4 2.3c-.3.3-.5.5-1 .5l.3-5 9.2-8.3c.4-.4-.1-.6-.6-.2L6.2 13 1.4 11.5c-1-.3-1-1 .2-1.5L20.5 2.9c.9-.3 1.6.2 1.4 1.4Z" />
        </svg>
        {t("linkButton")}
      </a>
      <ol className="m-0 flex list-decimal flex-col gap-1.5 pl-5 text-[14px] text-subtle">
        <li>{t("linkStep1")}</li>
        <li>{t("linkStep2")}</li>
        <li>{t("linkStep3")}</li>
      </ol>
      <p className="m-0 text-[13px] text-muted">{t("linkExpires")}</p>
    </div>
  );
}

function PhoneStep({ onSubmit }: { onSubmit: (phone: string) => Promise<void> }) {
  const t = useTranslations("login");
  const errorMessage = useErrorMessage();
  const schema = useMemo(
    () => z.object({ phone: z.string().refine((v) => normalizePhone(v) !== null, { message: t("invalidPhone") }) }),
    [t],
  );
  const { register, handleSubmit, formState, setError } = useForm<{ phone: string }>({
    resolver: zodResolver(schema),
    defaultValues: { phone: "+998 " },
  });

  return (
    <form
      noValidate
      className="flex flex-col gap-3"
      onSubmit={handleSubmit(async ({ phone }) => {
        try {
          await onSubmit(normalizePhone(phone)!);
        } catch (e) {
          setError("phone", { message: errorMessage(e) });
        }
      })}
    >
      <label className="flex flex-col gap-1.5">
        <span className="text-[14px] font-semibold">{t("phone")}</span>
        <input
          className="input num"
          type="tel"
          inputMode="tel"
          autoComplete="tel"
          placeholder={t("phoneHint")}
          aria-invalid={!!formState.errors.phone}
          {...register("phone")}
        />
      </label>
      {formState.errors.phone && <span className="text-[13px] text-danger">{formState.errors.phone.message}</span>}
      <button type="submit" className="btn btn-orange" disabled={formState.isSubmitting}>
        {t("sendCode")}
      </button>
    </form>
  );
}

function CodeStep({
  phone,
  resendAt,
  onChangePhone,
  onResend,
  onDone,
}: {
  phone: string;
  resendAt: number;
  onChangePhone: () => void;
  onResend: () => void;
  onDone: () => void;
}) {
  const t = useTranslations("login");
  const te = useTranslations("errors");
  const [now, setNow] = useState(() => Date.now());
  const schema = useMemo(() => z.object({ code: z.string().regex(/^\d{4,6}$/, t("invalidCode")) }), [t]);
  const { register, handleSubmit, formState, setError } = useForm<{ code: string }>({
    resolver: zodResolver(schema),
    defaultValues: { code: "" },
  });

  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, []);
  const seconds = Math.max(0, Math.ceil((resendAt - now) / 1000));

  return (
    <form
      noValidate
      className="flex flex-col gap-3"
      onSubmit={handleSubmit(async ({ code }) => {
        const res = await verifyOtp(phone, code).catch(() => null);
        if (!res) return setError("code", { message: te("network") });
        if (!res.ok) {
          const problem = (await res.json().catch(() => undefined)) as Problem | undefined;
          return setError("code", { message: new ApiError(res.status, problem).detail ?? te("generic") });
        }
        onDone();
      })}
    >
      <label className="flex flex-col gap-1.5">
        <span className="text-[14px] font-semibold">{t("code")}</span>
        <input
          className="input num tracking-[0.3em]"
          inputMode="numeric"
          autoComplete="one-time-code"
          maxLength={6}
          autoFocus
          aria-invalid={!!formState.errors.code}
          {...register("code")}
        />
      </label>
      {formState.errors.code && <span className="text-[13px] text-danger">{formState.errors.code.message}</span>}
      <button type="submit" className="btn btn-orange" disabled={formState.isSubmitting}>
        {t("verify")}
      </button>
      <div className="flex flex-wrap justify-between gap-2 text-[14px]">
        <button type="button" className="link-btn" onClick={onChangePhone}>
          {t("changePhone")}
        </button>
        {seconds > 0 ? (
          <span className="num text-muted">{t("resendIn", { seconds })}</span>
        ) : (
          <button type="button" className="link-btn" onClick={onResend}>
            {t("resend")}
          </button>
        )}
      </div>
    </form>
  );
}

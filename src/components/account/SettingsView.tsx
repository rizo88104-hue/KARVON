"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useLocale, useTranslations } from "next-intl";
import { useMemo } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { api } from "@/api/client";
import { unwrap } from "@/api/errors";
import type { NotificationSettings, User } from "@/api/types";
import { EmptyState } from "@/components/EmptyState";
import { useToast } from "@/components/Toast";
import { usePathname, useRouter } from "@/i18n/navigation";
import type { Locale } from "@/i18n/routing";
import { keys, useMe, useNotificationSettings } from "@/lib/queries";
import { updateUser } from "@/lib/session";
import { useErrorMessage } from "@/lib/use-error-message";

export function SettingsView() {
  const t = useTranslations("settings");
  const tc = useTranslations("common");
  const errorMessage = useErrorMessage();
  const me = useMe();
  const notifications = useNotificationSettings();

  if (me.isPending || notifications.isPending) {
    return (
      <p className="py-14 text-center text-muted" role="status">
        {tc("loading")}
      </p>
    );
  }
  if (me.isError || notifications.isError) {
    return <EmptyState title={t("error")} text={errorMessage(me.error ?? notifications.error)} />;
  }

  return (
    <>
      <h1 className="mt-0 mb-4 text-[24px] font-bold">{t("title")}</h1>
      <div className="flex flex-col gap-4">
        <ProfileCard user={me.data} />
        <LanguageCard user={me.data} />
        <NotificationsCard settings={notifications.data} />
      </div>
    </>
  );
}

function useSaveProfile() {
  const qc = useQueryClient();
  const toast = useToast();
  const t = useTranslations("settings");
  const errorMessage = useErrorMessage();
  return useMutation({
    mutationFn: async (body: { fullName?: string; language?: "UZ" | "RU" }) => unwrap(await api.PATCH("/api/v1/me", { body })),
    onSuccess: (user) => {
      qc.setQueryData(keys.me, user);
      updateUser(user);
      toast(t("saved"));
    },
    onError: (e) => toast(errorMessage(e)),
  });
}

function ProfileCard({ user }: { user: User }) {
  const t = useTranslations("settings");
  const tc = useTranslations("common");
  const save = useSaveProfile();
  const schema = useMemo(() => z.object({ fullName: z.string().trim().max(100) }), []);
  const { register, handleSubmit, formState } = useForm<{ fullName: string }>({
    resolver: zodResolver(schema),
    defaultValues: { fullName: user.fullName ?? "" },
  });

  return (
    <section className="card p-4 min-[561px]:p-5">
      <h2 className="mt-0 mb-3 text-[17px] font-bold">{t("profile")}</h2>
      <form
        noValidate
        className="flex flex-col gap-3 min-[561px]:max-w-[420px]"
        onSubmit={handleSubmit(({ fullName }) => save.mutateAsync({ fullName: fullName.trim() }).catch(() => undefined))}
      >
        <label className="flex flex-col gap-1 text-[14px]">
          <span className="font-semibold">{t("fullName")}</span>
          <input className="input" autoComplete="name" {...register("fullName")} />
        </label>
        <div className="flex flex-col gap-1 text-[14px]">
          <span className="font-semibold">{t("phone")}</span>
          <span className="num text-subtle">{user.phone}</span>
        </div>
        <button type="submit" className="btn btn-navy self-start" disabled={formState.isSubmitting}>
          {tc("save")}
        </button>
      </form>
    </section>
  );
}

function LanguageCard({ user }: { user: User }) {
  const t = useTranslations("settings");
  const tb = useTranslations("topbar");
  const locale = useLocale();
  const router = useRouter();
  const pathname = usePathname();
  const save = useSaveProfile();
  const current = user.language ?? (locale === "ru" ? "RU" : "UZ");

  return (
    <section className="card p-4 min-[561px]:p-5">
      <h2 className="mt-0 mb-1 text-[17px] font-bold">{t("language")}</h2>
      <p className="mt-0 mb-3 text-[14px] text-muted">{t("languageHint")}</p>
      <div className="flex gap-2" role="radiogroup" aria-label={t("language")}>
        {(["UZ", "RU"] as const).map((lang) => (
          <button
            key={lang}
            type="button"
            role="radio"
            aria-checked={current === lang}
            className="chip"
            disabled={save.isPending}
            onClick={async () => {
              await save.mutateAsync({ language: lang }).catch(() => undefined);
              const next = lang.toLowerCase() as Locale;
              if (next !== locale) router.replace(pathname, { locale: next });
            }}
          >
            {tb(lang === "UZ" ? "uz" : "ru")}
          </button>
        ))}
      </div>
    </section>
  );
}

function NotificationsCard({ settings }: { settings: NotificationSettings }) {
  const t = useTranslations("settings");
  const toast = useToast();
  const errorMessage = useErrorMessage();
  const qc = useQueryClient();

  const update = useMutation({
    mutationFn: async (body: { sms?: boolean; telegram?: boolean }) =>
      unwrap(await api.PATCH("/api/v1/me/notification-settings", { body })),
    onSuccess: (data) => {
      qc.setQueryData(keys.notifications, data);
      toast(t("saved"));
    },
    onError: (e) => toast(errorMessage(e)),
  });
  const link = useMutation({
    mutationFn: async () => unwrap(await api.GET("/api/v1/me/telegram-link")),
    onSuccess: (data) => {
      if (data.url) window.open(data.url, "_blank", "noopener");
      toast(t("linkOpened"));
    },
    onError: (e) => toast(errorMessage(e)),
  });

  const toggle = "flex cursor-pointer items-center justify-between gap-3 border-b border-line py-3 text-[15px] last:border-b-0";
  return (
    <section className="card p-4 min-[561px]:p-5">
      <h2 className="mt-0 mb-1 text-[17px] font-bold">{t("notifications")}</h2>
      {/* SMS is turned off for the whole shop for now: the switch appears only when the backend sends SMS. */}
      {settings.smsAvailable && (
        <label className={toggle}>
          <span>{t("sms")}</span>
          <input
            type="checkbox"
            className="h-5 w-5 accent-navy"
            checked={!!settings.sms}
            disabled={update.isPending}
            onChange={(e) => update.mutate({ sms: e.target.checked })}
          />
        </label>
      )}
      <label className={toggle}>
        <span>
          {t("telegram")}
          <span
            className={`block text-[13px] ${settings.telegramLinked ? "font-semibold text-success" : "text-muted"}`}
          >
            {settings.telegramLinked ? `✓ ${t("telegramLinked")}` : t("telegramNotLinked")}
          </span>
        </span>
        <input
          type="checkbox"
          className="h-5 w-5 accent-navy"
          checked={!!settings.telegram}
          disabled={update.isPending || !settings.telegramLinked}
          onChange={(e) => update.mutate({ telegram: e.target.checked })}
        />
      </label>
      {!settings.telegramLinked && (
        <button type="button" className="btn btn-outline mt-3" disabled={link.isPending} onClick={() => link.mutate()}>
          {t("linkTelegram")}
        </button>
      )}
    </section>
  );
}

"use client";

import { useQuery } from "@tanstack/react-query";
import { useLocale, useTranslations } from "next-intl";
import { useState } from "react";
import { api } from "@/api/client";
import { unwrap } from "@/api/errors";
import type {
  AdminBanner,
  AdminCategory,
  AdminPage,
  BlockedKeyword,
  BlockedKeywordRequest,
  PromoCode,
  PromoCodeRequest,
} from "@/api/types";
import { formatDateTime, formatDecimal } from "@/lib/format";
import { CrudSection, fromLocalInput, num, str, toLocalInput, type FormValues } from "./CrudSection";
import { AdminPageHeader } from "./ui";

type Tab = "banners" | "categories" | "keywords" | "promo" | "pages";
const TABS: Tab[] = ["banners", "categories", "keywords", "promo", "pages"];

export function ContentView() {
  const t = useTranslations("admin.content");
  const ta = useTranslations("admin");
  const [tab, setTab] = useState<Tab>("banners");
  return (
    <>
      <AdminPageHeader title={ta("nav.content")} />
      <div role="tablist" className="card no-scrollbar mb-4 flex gap-1 overflow-x-auto p-1.5">
        {TABS.map((k) => (
          <button
            key={k}
            type="button"
            role="tab"
            aria-selected={tab === k}
            onClick={() => setTab(k)}
            className={`rounded-sm border-0 px-4 py-2 text-[14px] whitespace-nowrap ${
              tab === k ? "bg-orange font-bold text-ink" : "bg-transparent hover:bg-grey-bg"
            }`}
          >
            {t(`tab.${k}`)}
          </button>
        ))}
      </div>
      {tab === "banners" && <Banners />}
      {tab === "categories" && <Categories />}
      {tab === "keywords" && <Keywords />}
      {tab === "promo" && <PromoCodes />}
      {tab === "pages" && <Pages />}
    </>
  );
}

const yesNo = (v?: boolean) => (v ? "✓" : "—");

function Banners() {
  const t = useTranslations("admin.content.banners");
  const locale = useLocale();
  const body = (v: FormValues) => ({
    title: String(v.title).trim(),
    subtitle: str(v.subtitle),
    imageUrl: String(v.imageUrl).trim(),
    linkUrl: str(v.linkUrl),
    sortOrder: num(v.sortOrder),
    active: !!v.active,
    activeFrom: fromLocalInput(v.activeFrom),
    activeTo: fromLocalInput(v.activeTo),
  });
  return (
    <CrudSection<AdminBanner>
      title={t("title")}
      queryKey={["admin", "content", "banners"]}
      load={async () => ({ rows: unwrap(await api.GET("/api/v1/admin/banners")), totalPages: 1 })}
      rowId={(b) => b.id ?? ""}
      rowLabel={(b) => b.title ?? ""}
      headers={[t("name"), t("link"), t("sortOrder"), t("active"), t("period")]}
      cells={(b) => [
        <b key="t">{b.title}</b>,
        <span key="l" className="text-muted">{b.linkUrl}</span>,
        <span key="s" className="num">{b.sortOrder}</span>,
        yesNo(b.active),
        <span key="p" className="num text-muted">
          {[formatDateTime(b.activeFrom, locale), formatDateTime(b.activeTo, locale)].filter(Boolean).join(" — ")}
        </span>,
      ]}
      fields={[
        { name: "title", label: t("name"), type: "text", required: true },
        { name: "subtitle", label: t("subtitle"), type: "textarea" },
        { name: "imageUrl", label: t("imageUrl"), type: "text", required: true },
        { name: "linkUrl", label: t("link"), type: "text", hint: t("linkHint") },
        { name: "sortOrder", label: t("sortOrder"), type: "number", half: true },
        { name: "active", label: t("active"), type: "checkbox", half: true },
        { name: "activeFrom", label: t("activeFrom"), type: "datetime", half: true },
        { name: "activeTo", label: t("activeTo"), type: "datetime", half: true },
      ]}
      toForm={(b) => ({
        title: b?.title ?? "",
        subtitle: b?.subtitle ?? "",
        imageUrl: b?.imageUrl ?? "",
        linkUrl: b?.linkUrl ?? "",
        sortOrder: b?.sortOrder != null ? String(b.sortOrder) : "0",
        active: b?.active ?? true,
        activeFrom: toLocalInput(b?.activeFrom),
        activeTo: toLocalInput(b?.activeTo),
      })}
      save={async (v, id) =>
        id
          ? unwrap(await api.PUT("/api/v1/admin/banners/{id}", { params: { path: { id } }, body: body(v) }))
          : unwrap(await api.POST("/api/v1/admin/banners", { body: body(v) }))
      }
      remove={async (id) => unwrap(await api.DELETE("/api/v1/admin/banners/{id}", { params: { path: { id } } }))}
    />
  );
}

function Categories() {
  const t = useTranslations("admin.content.categories");
  const all = useQuery({
    queryKey: ["admin", "content", "categories", "all"],
    queryFn: async () => unwrap(await api.GET("/api/v1/admin/categories")),
  });
  const parents = (all.data ?? []).filter((c) => !c.parentId);
  const nameOf = (id?: string) => all.data?.find((c) => c.id === id)?.nameUz ?? "";
  const body = (v: FormValues) => ({
    parentId: str(v.parentId),
    slug: String(v.slug).trim(),
    nameUz: String(v.nameUz).trim(),
    nameRu: String(v.nameRu).trim(),
    searchKeywordZh: String(v.searchKeywordZh).trim(),
    icon: str(v.icon),
    sortOrder: num(v.sortOrder),
  });
  return (
    <CrudSection<AdminCategory>
      title={t("title")}
      queryKey={["admin", "content", "categories"]}
      load={async () => {
        const rows = unwrap(await api.GET("/api/v1/admin/categories"));
        // Parents first, each followed by its children.
        const ordered = rows.filter((c) => !c.parentId).flatMap((p) => [p, ...rows.filter((c) => c.parentId === p.id)]);
        return { rows: ordered.length === rows.length ? ordered : rows, totalPages: 1 };
      }}
      rowId={(c) => c.id ?? ""}
      rowLabel={(c) => c.nameUz ?? ""}
      headers={[t("nameUz"), t("nameRu"), t("slug"), t("keyword"), t("parent"), t("sortOrder")]}
      cells={(c) => [
        <span key="n" className={c.parentId ? "pl-4 text-subtle" : "font-semibold"}>
          {c.parentId ? "↳ " : ""}
          {c.nameUz}
        </span>,
        c.nameRu,
        <span key="s" className="num">{c.slug}</span>,
        <span key="k" lang="zh">{c.searchKeywordZh}</span>,
        nameOf(c.parentId),
        <span key="o" className="num">{c.sortOrder}</span>,
      ]}
      fields={[
        { name: "nameUz", label: t("nameUz"), type: "text", required: true, half: true },
        { name: "nameRu", label: t("nameRu"), type: "text", required: true, half: true },
        { name: "slug", label: t("slug"), type: "text", required: true, half: true, hint: t("slugHint") },
        { name: "searchKeywordZh", label: t("keyword"), type: "text", required: true, half: true },
        {
          name: "parentId",
          label: t("parent"),
          type: "select",
          half: true,
          options: parents.map((p) => ({ value: p.id ?? "", label: p.nameUz ?? "" })),
        },
        { name: "sortOrder", label: t("sortOrder"), type: "number", half: true },
        { name: "icon", label: t("icon"), type: "text" },
      ]}
      toForm={(c) => ({
        nameUz: c?.nameUz ?? "",
        nameRu: c?.nameRu ?? "",
        slug: c?.slug ?? "",
        searchKeywordZh: c?.searchKeywordZh ?? "",
        parentId: c?.parentId ?? "",
        sortOrder: c?.sortOrder != null ? String(c.sortOrder) : "0",
        icon: c?.icon ?? "",
      })}
      save={async (v, id) =>
        id
          ? unwrap(await api.PUT("/api/v1/admin/categories/{id}", { params: { path: { id } }, body: body(v) }))
          : unwrap(await api.POST("/api/v1/admin/categories", { body: body(v) }))
      }
      remove={async (id) => unwrap(await api.DELETE("/api/v1/admin/categories/{id}", { params: { path: { id } } }))}
    />
  );
}

const KEYWORD_LANGS: BlockedKeywordRequest["lang"][] = ["UZ", "RU", "ZH"];
const KEYWORD_CATEGORIES: BlockedKeywordRequest["category"][] = ["LIQUID", "BATTERY", "WEAPON", "MEDICINE", "REPLICA", "OTHER"];

function Keywords() {
  const t = useTranslations("admin.content.keywords");
  const [q, setQ] = useState("");
  const [lang, setLang] = useState("");
  const body = (v: FormValues): BlockedKeywordRequest => ({
    keyword: String(v.keyword).trim(),
    lang: v.lang as BlockedKeywordRequest["lang"],
    category: v.category as BlockedKeywordRequest["category"],
  });
  return (
    <CrudSection<BlockedKeyword>
      title={t("title")}
      queryKey={["admin", "content", "keywords", q, lang]}
      filters={
        <div className="flex flex-wrap gap-2">
          <input className="input h-9 max-w-[240px]" placeholder={t("search")} aria-label={t("search")} value={q} onChange={(e) => setQ(e.target.value)} />
          <select className="input h-9 w-auto" value={lang} onChange={(e) => setLang(e.target.value)} aria-label={t("lang")}>
            <option value="">{t("allLangs")}</option>
            {KEYWORD_LANGS.map((l) => (
              <option key={l} value={l}>
                {l}
              </option>
            ))}
          </select>
        </div>
      }
      load={async (page) => {
        const res = unwrap(
          await api.GET("/api/v1/admin/blocked-keywords", {
            params: {
              query: {
                q: q.trim() || undefined,
                lang: (lang || undefined) as BlockedKeywordRequest["lang"] | undefined,
                page,
                size: 50,
              },
            },
          }),
        );
        return { rows: res.content ?? [], totalPages: res.totalPages ?? 1 };
      }}
      rowId={(k) => k.id ?? ""}
      rowLabel={(k) => k.keyword ?? ""}
      headers={[t("keyword"), t("lang"), t("category")]}
      cells={(k) => [<b key="k">{k.keyword}</b>, k.lang, k.category ? t(`categoryName.${k.category}`) : ""]}
      fields={[
        { name: "keyword", label: t("keyword"), type: "text", required: true },
        { name: "lang", label: t("lang"), type: "select", required: true, half: true, options: KEYWORD_LANGS.map((l) => ({ value: l, label: l })) },
        {
          name: "category",
          label: t("category"),
          type: "select",
          required: true,
          half: true,
          options: KEYWORD_CATEGORIES.map((c) => ({ value: c, label: t(`categoryName.${c}`) })),
        },
      ]}
      toForm={(k) => ({ keyword: k?.keyword ?? "", lang: k?.lang ?? "UZ", category: k?.category ?? "OTHER" })}
      save={async (v, id) =>
        id
          ? unwrap(await api.PUT("/api/v1/admin/blocked-keywords/{id}", { params: { path: { id } }, body: body(v) }))
          : unwrap(await api.POST("/api/v1/admin/blocked-keywords", { body: body(v) }))
      }
      remove={async (id) => unwrap(await api.DELETE("/api/v1/admin/blocked-keywords/{id}", { params: { path: { id } } }))}
    />
  );
}

function PromoCodes() {
  const t = useTranslations("admin.content.promo");
  const locale = useLocale();
  const [q, setQ] = useState("");
  const body = (v: FormValues): PromoCodeRequest => ({
    code: String(v.code).trim().toUpperCase(),
    type: v.type as PromoCodeRequest["type"],
    value: num(v.value) ?? 0,
    maxUses: num(v.maxUses),
    perUserLimit: num(v.perUserLimit),
    validFrom: fromLocalInput(v.validFrom),
    validTo: fromLocalInput(v.validTo),
    active: !!v.active,
  });
  return (
    <CrudSection<PromoCode>
      title={t("title")}
      queryKey={["admin", "content", "promo", q]}
      filters={<input className="input h-9 max-w-[240px]" placeholder={t("search")} aria-label={t("search")} value={q} onChange={(e) => setQ(e.target.value)} />}
      load={async (page) => {
        const res = unwrap(await api.GET("/api/v1/admin/promo-codes", { params: { query: { q: q.trim() || undefined, page, size: 50 } } }));
        return { rows: res.content ?? [], totalPages: res.totalPages ?? 1 };
      }}
      rowId={(p) => p.id ?? ""}
      rowLabel={(p) => p.code ?? ""}
      headers={[t("code"), t("type"), t("value"), t("used"), t("period"), t("active")]}
      cells={(p) => [
        <b key="c" className="num">{p.code}</b>,
        p.type ? t(`typeName.${p.type}`) : "",
        <span key="v" className="num">{formatDecimal(p.value)}</span>,
        <span key="u" className="num">
          {p.usedCount ?? 0}
          {p.maxUses ? ` / ${p.maxUses}` : ""}
        </span>,
        <span key="p" className="num text-muted">
          {[formatDateTime(p.validFrom, locale), formatDateTime(p.validTo, locale)].filter(Boolean).join(" — ")}
        </span>,
        yesNo(p.active),
      ]}
      fields={[
        { name: "code", label: t("code"), type: "text", required: true, half: true },
        {
          name: "type",
          label: t("type"),
          type: "select",
          required: true,
          half: true,
          options: (["PERCENT_OF_FEE", "FIXED_UZS"] as const).map((v) => ({ value: v, label: t(`typeName.${v}`) })),
        },
        { name: "value", label: t("value"), type: "number", required: true, half: true, hint: t("valueHint") },
        { name: "active", label: t("active"), type: "checkbox", half: true },
        { name: "maxUses", label: t("maxUses"), type: "number", half: true },
        { name: "perUserLimit", label: t("perUserLimit"), type: "number", half: true },
        { name: "validFrom", label: t("validFrom"), type: "datetime", half: true },
        { name: "validTo", label: t("validTo"), type: "datetime", half: true },
      ]}
      toForm={(p) => ({
        code: p?.code ?? "",
        type: p?.type ?? "PERCENT_OF_FEE",
        value: p?.value != null ? String(p.value) : "",
        active: p?.active ?? true,
        maxUses: p?.maxUses != null ? String(p.maxUses) : "",
        perUserLimit: p?.perUserLimit != null ? String(p.perUserLimit) : "",
        validFrom: toLocalInput(p?.validFrom),
        validTo: toLocalInput(p?.validTo),
      })}
      save={async (v, id) =>
        id
          ? unwrap(await api.PUT("/api/v1/admin/promo-codes/{id}", { params: { path: { id } }, body: body(v) }))
          : unwrap(await api.POST("/api/v1/admin/promo-codes", { body: body(v) }))
      }
      remove={async (id) => unwrap(await api.DELETE("/api/v1/admin/promo-codes/{id}", { params: { path: { id } } }))}
    />
  );
}

function Pages() {
  const t = useTranslations("admin.content.pages");
  const locale = useLocale();
  const body = (v: FormValues) => ({
    slug: String(v.slug).trim(),
    titleUz: String(v.titleUz).trim(),
    titleRu: String(v.titleRu).trim(),
    bodyUz: String(v.bodyUz),
    bodyRu: String(v.bodyRu),
    published: !!v.published,
  });
  return (
    <CrudSection<AdminPage>
      title={t("title")}
      queryKey={["admin", "content", "pages"]}
      load={async () => ({ rows: unwrap(await api.GET("/api/v1/admin/pages")), totalPages: 1 })}
      rowId={(p) => p.id ?? ""}
      rowLabel={(p) => p.slug ?? ""}
      headers={[t("slug"), t("titleUz"), t("published"), t("updated")]}
      cells={(p) => [
        <b key="s" className="num">/pages/{p.slug}</b>,
        p.titleUz,
        yesNo(p.published),
        <span key="u" className="num text-muted">{formatDateTime(p.updatedAt, locale)}</span>,
      ]}
      fields={[
        { name: "slug", label: t("slug"), type: "text", required: true, half: true, hint: t("slugHint") },
        { name: "published", label: t("published"), type: "checkbox", half: true },
        { name: "titleUz", label: t("titleUz"), type: "text", required: true, half: true },
        { name: "titleRu", label: t("titleRu"), type: "text", required: true, half: true },
        { name: "bodyUz", label: t("bodyUz"), type: "markdown", required: true },
        { name: "bodyRu", label: t("bodyRu"), type: "markdown", required: true },
      ]}
      toForm={(p) => ({
        slug: p?.slug ?? "",
        published: p?.published ?? true,
        titleUz: p?.titleUz ?? "",
        titleRu: p?.titleRu ?? "",
        bodyUz: p?.bodyUz ?? "",
        bodyRu: p?.bodyRu ?? "",
      })}
      save={async (v, id) =>
        id
          ? unwrap(await api.PUT("/api/v1/admin/pages/{id}", { params: { path: { id } }, body: body(v) }))
          : unwrap(await api.POST("/api/v1/admin/pages", { body: body(v) }))
      }
      remove={async (id) => unwrap(await api.DELETE("/api/v1/admin/pages/{id}", { params: { path: { id } } }))}
    />
  );
}

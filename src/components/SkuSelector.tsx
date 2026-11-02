"use client";

import { useTranslations } from "next-intl";
import type { Sku } from "@/api/types";

/** Selected value per property, keyed by the original (Chinese) property name. */
export type SkuSelection = Record<string, string>;

type Group = { key: string; name: string; options: { key: string; label: string }[] };

export function skuGroups(skus: Sku[]): Group[] {
  const groups = new Map<string, Group>();
  for (const sku of skus) {
    for (const p of sku.properties ?? []) {
      const key = p.nameOriginal ?? p.name ?? "";
      const group = groups.get(key) ?? { key, name: p.name ?? key, options: [] };
      const value = p.valueOriginal ?? p.value ?? "";
      if (!group.options.some((o) => o.key === value)) group.options.push({ key: value, label: p.value ?? value });
      groups.set(key, group);
    }
  }
  return [...groups.values()];
}

function matches(sku: Sku, selection: SkuSelection) {
  return Object.entries(selection).every(([name, value]) =>
    (sku.properties ?? []).some((p) => (p.nameOriginal ?? p.name) === name && (p.valueOriginal ?? p.value) === value),
  );
}

/** The SKU matching every group, or null while the selection is incomplete. */
export function findSku(skus: Sku[], selection: SkuSelection): Sku | null {
  if (skus.length === 1) return skus[0];
  const groups = skuGroups(skus);
  if (groups.some((g) => !selection[g.key])) return null;
  return skus.find((s) => matches(s, selection)) ?? null;
}

/** First in-stock SKU as the initial selection. */
export function initialSelection(skus: Sku[]): SkuSelection {
  const sku = skus.find((s) => (s.stock ?? 0) > 0) ?? skus[0];
  const selection: SkuSelection = {};
  for (const p of sku?.properties ?? []) selection[p.nameOriginal ?? p.name ?? ""] = p.valueOriginal ?? p.value ?? "";
  return selection;
}

/** One row of chips per property (color, size…). Options with no stock for the current choice are disabled. */
export function SkuSelector({
  skus,
  value,
  onChange,
}: {
  skus: Sku[];
  value: SkuSelection;
  onChange: (value: SkuSelection) => void;
}) {
  const t = useTranslations("sku");
  const groups = skuGroups(skus);
  if (skus.length <= 1 || groups.length === 0) return null;

  return (
    <div className="flex flex-col gap-3">
      {groups.map((group) => (
        <div key={group.key} className="grid grid-cols-1 gap-2 sm:grid-cols-[110px_1fr] sm:gap-3">
          <span className="pt-2 text-[14px] text-muted">{group.name}</span>
          <div className="flex flex-wrap gap-2" role="radiogroup" aria-label={group.name}>
            {group.options.map((option) => {
              const next = { ...value, [group.key]: option.key };
              const available = skus.some((s) => matches(s, next) && (s.stock ?? 0) > 0);
              const checked = value[group.key] === option.key;
              return (
                <button
                  key={option.key}
                  type="button"
                  role="radio"
                  aria-checked={checked}
                  className="chip"
                  disabled={!available}
                  title={available ? undefined : t("unavailable", { value: option.label })}
                  onClick={() => onChange(next)}
                >
                  {option.label}
                </button>
              );
            })}
          </div>
        </div>
      ))}
    </div>
  );
}

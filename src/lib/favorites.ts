"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, useSyncExternalStore } from "react";
import { api } from "@/api/client";
import { unwrap } from "@/api/errors";
import type { ProductCard } from "@/api/types";
import { useSession } from "./use-session";

// "Sevimlilar". Signed in: kept by the backend (/api/v1/me/favorites). Guest: kept in this browser (localStorage),
// as the cards the guest saw, and moved to the account after logging in (useFavoritesMerge).

export const favoriteKeys = {
  ids: ["favorites", "ids"] as const,
  list: (locale: string) => ["favorites", "list", locale] as const,
};

const STORAGE_KEY = "em_favorites";
/** The backend keeps at most 200; the merge request takes at most 100. */
const GUEST_MAX = 100;

// ---- guest list in localStorage (newest first), shared by every component through useSyncExternalStore
let guestCache: ProductCard[] | null = null;
const listeners = new Set<() => void>();
const EMPTY: ProductCard[] = [];

function readGuest(): ProductCard[] {
  if (guestCache) return guestCache;
  try {
    const parsed = JSON.parse(window.localStorage.getItem(STORAGE_KEY) ?? "[]");
    guestCache = Array.isArray(parsed) ? parsed.filter((c) => typeof c?.externalId === "string") : [];
  } catch {
    guestCache = [];
  }
  return guestCache;
}

function writeGuest(list: ProductCard[]) {
  guestCache = list.slice(0, GUEST_MAX);
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(guestCache));
  } catch {
    // private mode or full storage: the list still works until the page is closed
  }
  listeners.forEach((l) => l());
}

function subscribeGuest(listener: () => void) {
  listeners.add(listener);
  const onStorage = (e: StorageEvent) => {
    if (e.key === STORAGE_KEY) {
      guestCache = null;
      listener();
    }
  };
  window.addEventListener("storage", onStorage);
  return () => {
    listeners.delete(listener);
    window.removeEventListener("storage", onStorage);
  };
}

export function useGuestFavorites(): ProductCard[] {
  return useSyncExternalStore(subscribeGuest, readGuest, () => EMPTY);
}

// ---- the hook used by the heart buttons and the header
export function useFavorites() {
  const status = useSession().status;
  const authed = status === "authenticated";
  const qc = useQueryClient();
  const guest = useGuestFavorites();

  const idsQuery = useQuery({
    queryKey: favoriteKeys.ids,
    queryFn: async () => unwrap(await api.GET("/api/v1/me/favorites/ids")),
    enabled: authed,
  });

  const mutation = useMutation({
    mutationFn: async ({ id, add }: { id: string; add: boolean }) => {
      const path = { params: { path: { externalId: id } } };
      const res = add
        ? await api.PUT("/api/v1/me/favorites/{externalId}", path)
        : await api.DELETE("/api/v1/me/favorites/{externalId}", path);
      return unwrap(res);
    },
    // Optimistic: the heart changes at once and goes back if the request fails.
    onMutate: async ({ id, add }) => {
      await qc.cancelQueries({ queryKey: favoriteKeys.ids });
      const before = qc.getQueryData<string[]>(favoriteKeys.ids);
      qc.setQueryData<string[]>(favoriteKeys.ids, (ids = []) =>
        add ? [id, ...ids.filter((x) => x !== id)] : ids.filter((x) => x !== id),
      );
      return { before };
    },
    onError: (_error, _vars, context) => qc.setQueryData(favoriteKeys.ids, context?.before),
    onSettled: () => qc.invalidateQueries({ queryKey: ["favorites"] }),
  });

  const ids = authed ? (idsQuery.data ?? []) : guest.map((c) => c.externalId);
  const set = new Set(ids);

  /** Adds or removes the product; answers false when a signed-in request failed (the error is thrown). */
  async function toggle(card: ProductCard) {
    const id = card.externalId;
    if (!id) return;
    const add = !set.has(id);
    if (!authed) {
      const rest = guest.filter((c) => c.externalId !== id);
      writeGuest(add ? [card, ...rest] : rest);
      return;
    }
    await mutation.mutateAsync({ id, add });
  }

  return {
    ready: status !== "unknown" && (!authed || idsQuery.isSuccess),
    count: ids.length,
    has: (id?: string) => !!id && set.has(id),
    toggle,
  };
}

/** After logging in, moves the guest's browser favorites to the account; what could not be saved stays local. */
export function useFavoritesMerge() {
  const authed = useSession().status === "authenticated";
  const guest = useGuestFavorites();
  const qc = useQueryClient();
  const running = useRef(false);

  useEffect(() => {
    if (!authed || guest.length === 0 || running.current) return;
    running.current = true;
    const externalIds = guest.map((c) => c.externalId).filter((id): id is string => !!id);
    api
      .POST("/api/v1/me/favorites/merge", { body: { externalIds } })
      .then((res) => {
        const saved = new Set(unwrap(res));
        writeGuest(readGuest().filter((c) => !saved.has(c.externalId ?? "")));
        qc.setQueryData(favoriteKeys.ids, [...saved]);
        qc.invalidateQueries({ queryKey: ["favorites", "list"] });
      })
      .catch(() => {
        // offline or the API is down: try again on the next page load
      })
      .finally(() => {
        running.current = false;
      });
  }, [authed, guest, qc]);
}

"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "@/api/client";
import { unwrap } from "@/api/errors";
import type { Cart } from "@/api/types";
import { useSession } from "./use-session";

export const keys = {
  cart: ["cart"] as const,
  addresses: ["addresses"] as const,
  wallet: (page: number) => ["wallet", page] as const,
  me: ["me"] as const,
  notifications: ["notification-settings"] as const,
  pricing: ["settings", "pricing"] as const,
  orders: (tab: string) => ["orders", tab] as const,
  order: (number: string) => ["order", number] as const,
  inbox: (locale: string) => ["inbox", "list", locale] as const,
  unread: ["inbox", "unread"] as const,
};

/** Queries that need a logged-in user wait until the session is known. */
function useAuthed() {
  return useSession().status === "authenticated";
}

export function useCart() {
  const authed = useAuthed();
  return useQuery({
    queryKey: keys.cart,
    queryFn: async () => unwrap(await api.GET("/api/v1/cart")),
    enabled: authed,
  });
}

/** Cart mutations answer the whole cart; write it straight into the cache. */
export function useCartMutation<V>(fn: (vars: V) => Promise<Cart>) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: (cart) => qc.setQueryData(keys.cart, cart),
  });
}

export function useAddresses() {
  const authed = useAuthed();
  return useQuery({
    queryKey: keys.addresses,
    queryFn: async () => unwrap(await api.GET("/api/v1/me/addresses")),
    enabled: authed,
  });
}

export function useWallet(page = 0, size = 20) {
  const authed = useAuthed();
  return useQuery({
    queryKey: keys.wallet(page),
    queryFn: async () => unwrap(await api.GET("/api/v1/me/wallet", { params: { query: { page, size } } })),
    enabled: authed,
  });
}

export function useMe() {
  const authed = useAuthed();
  return useQuery({
    queryKey: keys.me,
    queryFn: async () => unwrap(await api.GET("/api/v1/me")),
    enabled: authed,
  });
}

export function useNotificationSettings() {
  const authed = useAuthed();
  return useQuery({
    queryKey: keys.notifications,
    queryFn: async () => unwrap(await api.GET("/api/v1/me/notification-settings")),
    enabled: authed,
  });
}

export function usePricingSettings() {
  return useQuery({
    queryKey: keys.pricing,
    queryFn: async () => unwrap(await api.GET("/api/v1/settings/pricing")),
    staleTime: 5 * 60_000,
  });
}

const INBOX_SIZE = 20;

/** In-app notifications (the bell): what Telegram could not deliver. The text comes in the site language. */
export function useInbox(locale: string, enabled: boolean) {
  const authed = useAuthed();
  return useQuery({
    queryKey: keys.inbox(locale),
    queryFn: async () =>
      unwrap(await api.GET("/api/v1/me/notifications", { params: { query: { page: 0, size: INBOX_SIZE } } })),
    enabled: authed && enabled,
  });
}

/** Polled once a minute (and on window focus) so the badge stays current. */
export function useUnreadCount() {
  const authed = useAuthed();
  return useQuery({
    queryKey: keys.unread,
    queryFn: async () => unwrap(await api.GET("/api/v1/me/notifications/unread-count")),
    enabled: authed,
    refetchInterval: 60_000,
  });
}

/** Marks one notification (or all with no id) read, then refreshes the badge and the list. */
export function useMarkRead() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async (id?: string) => {
      const res = id
        ? await api.POST("/api/v1/me/notifications/{id}/read", { params: { path: { id } } })
        : await api.POST("/api/v1/me/notifications/read");
      unwrap(res);
    },
    onSettled: () => qc.invalidateQueries({ queryKey: ["inbox"] }),
  });
}

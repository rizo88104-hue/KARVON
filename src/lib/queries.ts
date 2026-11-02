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

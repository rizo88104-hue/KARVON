// Browser session. The access token lives only in memory (this module). The refresh token is an httpOnly
// cookie that only the Next.js route handlers under /api/auth/* can read; they talk to the backend for us.
import type { AuthResponse, User } from "@/api/types";

type SessionState = { status: "unknown" | "guest" | "authenticated"; user: User | null };

let accessToken: string | null = null;
let state: SessionState = { status: "unknown", user: null };
let refreshing: Promise<boolean> | null = null;
const listeners = new Set<() => void>();

function emit() {
  for (const listener of listeners) listener();
}

export function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

export function getSessionState(): SessionState {
  return state;
}

const SERVER_STATE: SessionState = { status: "unknown", user: null };
export function getServerSessionState(): SessionState {
  return SERVER_STATE;
}

export function getAccessToken() {
  return accessToken;
}

function setSession(auth: Pick<AuthResponse, "accessToken" | "user"> | null) {
  accessToken = auth?.accessToken ?? null;
  state = auth ? { status: "authenticated", user: auth.user ?? null } : { status: "guest", user: null };
  emit();
}

export function updateUser(user: User) {
  state = { ...state, user };
  emit();
}

/**
 * Gets a new access token with the refresh cookie. Concurrent callers share one request: the backend rotates
 * refresh tokens and treats a reused one as theft, so two parallel refreshes would end the session.
 */
export function refreshSession(): Promise<boolean> {
  if (!refreshing) {
    refreshing = fetch("/api/auth/refresh", { method: "POST", credentials: "same-origin" })
      .then(async (res) => {
        if (!res.ok) {
          setSession(null);
          return false;
        }
        setSession((await res.json()) as AuthResponse);
        return true;
      })
      .catch(() => {
        setSession(null);
        return false;
      })
      .finally(() => {
        refreshing = null;
      });
  }
  return refreshing;
}

export async function verifyOtp(phone: string, code: string): Promise<Response> {
  const res = await fetch("/api/auth/verify", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ phone, code }),
  });
  if (res.ok) setSession((await res.clone().json()) as AuthResponse);
  return res;
}

export async function logout() {
  await fetch("/api/auth/logout", { method: "POST" }).catch(() => undefined);
  setSession(null);
}

/** Called once on page load: restores the session from the refresh cookie, or marks the visitor a guest. */
export function initSession() {
  if (state.status !== "unknown") return;
  const hasFlag = document.cookie.split("; ").some((c) => c.startsWith("em_session="));
  if (hasFlag) {
    void refreshSession();
  } else {
    setSession(null);
  }
}

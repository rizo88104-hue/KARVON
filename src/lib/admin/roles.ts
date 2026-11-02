import type { Role, User } from "@/api/types";

export const STAFF_ROLES: Role[] = ["OPERATOR", "BUYER", "ADMIN"];

/** Sections of the staff panel and the roles that see them (same rules as the backend's @PreAuthorize). */
export const ADMIN_SECTIONS = [
  { key: "dashboard", href: "/admin/dashboard", roles: ["ADMIN", "OPERATOR"] },
  { key: "orders", href: "/admin/orders", roles: ["OPERATOR", "ADMIN"] },
  { key: "queue", href: "/admin/queue", roles: ["BUYER", "ADMIN"] },
  { key: "customers", href: "/admin/customers", roles: ["OPERATOR", "ADMIN"] },
  { key: "settings", href: "/admin/settings", roles: ["ADMIN"] },
  { key: "finance", href: "/admin/finance", roles: ["ADMIN"] },
  { key: "content", href: "/admin/content", roles: ["ADMIN"] },
  { key: "staff", href: "/admin/staff", roles: ["ADMIN"] },
  { key: "notifications", href: "/admin/notifications", roles: ["ADMIN"] },
] as const satisfies readonly { key: string; href: string; roles: Role[] }[];

export type AdminSectionKey = (typeof ADMIN_SECTIONS)[number]["key"];

export function hasRole(user: User | null | undefined, ...roles: Role[]): boolean {
  return !!user?.roles?.some((r) => roles.includes(r));
}

export function isStaff(user: User | null | undefined): boolean {
  return hasRole(user, ...STAFF_ROLES);
}

export function visibleSections(user: User | null | undefined) {
  return ADMIN_SECTIONS.filter((s) => hasRole(user, ...s.roles));
}

/** Spring "pageable" query objects must be sent as plain page=…&size=…, not pageable[page]=…. */
export const FORM_QUERY = { object: { style: "form", explode: true } } as const;

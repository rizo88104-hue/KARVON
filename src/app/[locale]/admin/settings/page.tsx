import { SettingsView } from "@/components/admin/SettingsView";
import { adminPageMetadata } from "@/lib/server/admin-metadata";

export const generateMetadata = adminPageMetadata("settings");

export default function Page() {
  return <SettingsView />;
}

export interface Tab {
  href: "/home" | "/journal" | "/hero" | "/settings";
  label: string;
}

/** Plan §4: HOME · JOURNAL · HERO · SETTINGS (the Arena is off for the first release). */
export const TABS: Tab[] = [
  { href: "/home", label: "HOME" },
  { href: "/journal", label: "JOURNAL" },
  { href: "/hero", label: "HERO" },
  { href: "/settings", label: "SETTINGS" },
];

export function isActiveTab(pathname: string, href: Tab["href"]): boolean {
  return pathname === href || pathname.startsWith(`${href}/`);
}

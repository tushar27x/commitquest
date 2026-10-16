export interface Tab {
  href: "/home" | "/journal" | "/hero" | "/arena" | "/settings";
  label: string;
}

/** Plan §4: HOME · JOURNAL · HERO · ARENA · SETTINGS. */
export const TABS: Tab[] = [
  { href: "/home", label: "HOME" },
  { href: "/journal", label: "JOURNAL" },
  { href: "/hero", label: "HERO" },
  { href: "/arena", label: "ARENA" },
  { href: "/settings", label: "SETTINGS" },
];

export function isActiveTab(pathname: string, href: Tab["href"]): boolean {
  return pathname === href || pathname.startsWith(`${href}/`);
}

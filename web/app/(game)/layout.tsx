import { GameShell } from "@/components/game/GameShell";

export default function GameLayout({ children }: LayoutProps<"/">) {
  return <GameShell>{children}</GameShell>;
}

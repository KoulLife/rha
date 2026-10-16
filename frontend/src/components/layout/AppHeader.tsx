import { useRef } from "react";
import type { ReactNode } from "react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";

type AppHeaderProps = {
  location: ReactNode;
  name: string;
  unreadCount: number;
  onNotificationClick: () => void;
  onLogout: (trigger?: HTMLElement) => void;
};

function AppHeader({ location, name, unreadCount, onNotificationClick, onLogout }: AppHeaderProps) {
  const accountTrigger = useRef<HTMLButtonElement>(null);
  return (
    <header className="topbar">
      <div className="app-header-location">{location}</div>
      <Button
        variant="ghost"
        onClick={onNotificationClick}
      >
        알림 <Badge variant="outline">{unreadCount}</Badge>
      </Button>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button
            ref={accountTrigger}
            variant="ghost"
            aria-label={`${name} 계정 메뉴`}
          >
            {name} · 계정 메뉴
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          <DropdownMenuItem onSelect={() => onLogout(accountTrigger.current ?? undefined)}>
            로그아웃
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </header>
  );
}
export { AppHeader };

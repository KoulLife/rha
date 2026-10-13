import { useState } from "react";
import { PageTitle } from "@/components/ui/PageTitle";
import { Pagination } from "@/components/ui/Pagination";
import { MenuPanel, MenuEmpty } from "@/components/layout/MenuPanel";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Field, FieldLabel } from "@/components/ui/field";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { Dialog } from "@/components/ui/Dialog";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { seoulDate, seoulTime, type DashboardNotification } from "@/features/dashboard/model";

export function NotificationsPage({ items, onRead }: { items: DashboardNotification[]; onRead: (id: number) => void }) {
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const rows = items.filter(item => !unreadOnly || !item.read);
  const unread = items.filter(item => !item.read).length;
  const selected = items.find(item => item.id === selectedId);
  return <div className="menu-page"><PageTitle title="내 알림" description="내 계정으로 받은 알림을 확인하고 읽음으로 표시합니다." action={<Button variant="outline" disabled={!unread} onClick={() => items.filter(item => !item.read).forEach(item => onRead(item.id))}>모든 알림 읽음으로 표시</Button>} />
    <div className="menu-results"><strong>읽지 않은 알림 {unread}건</strong><Field orientation="horizontal" className="menu-checkbox-field"><Checkbox id="notification-unread" checked={unreadOnly} onCheckedChange={value => setUnreadOnly(value === true)} /><FieldLabel htmlFor="notification-unread">읽지 않은 알림만 보기</FieldLabel></Field></div>
    <MenuPanel variant="table">{rows.length ? <Table variant="management"><TableHeader><TableRow><TableHead>알림</TableHead><TableHead>분류</TableHead><TableHead>받은 시간</TableHead><TableHead>상태</TableHead><TableHead>상세</TableHead></TableRow></TableHeader><TableBody>{rows.map(item => <TableRow key={item.id}><TableCell><strong>{item.title}</strong><p className="menu-muted">{item.message}</p></TableCell><TableCell>{item.category ?? "안내"}</TableCell><TableCell>{seoulDate(item.createdAt)} {seoulTime(item.createdAt)}</TableCell><TableCell><StatusBadge domain="notification" status={item.read ? "READ" : "UNREAD"} /></TableCell><TableCell><Button variant="ghost" onClick={() => setSelectedId(item.id)} aria-label={`${item.title} 알림 상세 보기`}>상세 보기</Button></TableCell></TableRow>)}</TableBody></Table> : <MenuEmpty title="표시할 알림이 없습니다" description="새 알림이 도착하면 여기에서 확인할 수 있습니다." />}</MenuPanel>
    <Pagination total={rows.length} />
    {selected && <Dialog dialog={{ title: "알림 상세", body: `${selected.title}\n\n${selected.message}\n\n받은 시간: ${seoulDate(selected.createdAt)} ${seoulTime(selected.createdAt)}`, action: selected.read ? undefined : "읽음으로 표시" }} onClose={() => setSelectedId(null)} onConfirm={() => onRead(selected.id)} />}
  </div>;
}

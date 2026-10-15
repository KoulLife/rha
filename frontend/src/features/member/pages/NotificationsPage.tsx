import { useState } from "react";
import * as api from "@/api/generated/api";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Field, FieldLabel } from "@/components/ui/field";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { AdminPage, ActionDialog, DataTable, QueryState, dateTime, type Action } from "@/features/admin/AdminUI";
import { Pagination, PAGE_SIZE } from "@/components/ui/Pagination";
import type { Screen } from "@/app/routes";
import type { UserRole } from "@/config/access";
import { notificationCategories, useNotificationDetail } from "./NotificationDetail";

type Navigate = (screen: Screen, id?: number) => void;

export function NotificationsPage({ role, onNavigate }: { role: UserRole; onNavigate: Navigate }) {
  const [page, setPage] = useState(1);
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [typeFilter, setTypeFilter] = useState("ALL");
  const [action, setAction] = useState<Action | null>(null);
  const detail = useNotificationDetail(role, onNavigate);
  const query = api.useGetMyNotifications({ page: 1, size: 100 });
  const unread = api.useGetMyUnreadNotificationCount();
  const readAll = api.useReadAllNotifications();
  const unreadCount = unread.data?.unreadCount ?? 0;
  const notifications = query.data?.content ?? [];
  const types = [...new Set(notifications.map(notification => notification.type))];
  const filtered = notifications.filter(notification => (!unreadOnly || !notification.read) && (typeFilter === "ALL" || notification.type === typeFilter));
  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const currentPage = Math.min(page, totalPages);
  const currentRows = filtered.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE);

  return <AdminPage title="내 알림" description="계정으로 받은 알림을 확인하고 관련 화면으로 이동합니다.">
    <div className="admin-notification-controls">
      <fieldset className="admin-notification-type-filters">
        <legend className="sr-only">알림 유형</legend>
      <div className="admin-notification-radio-group" role="radiogroup" aria-label="알림 유형">
        {["ALL", ...types].map(type => <label key={type} className="admin-notification-type-option">
          <input type="radio" name="notification-type" value={type} checked={typeFilter === type} onChange={() => { setTypeFilter(type); setPage(1); }} />
          <span>{type === "ALL" ? "전체" : notificationCategories[type] ?? type}</span>
        </label>)}
      </div>
      </fieldset>
      <Field orientation="horizontal" className="menu-checkbox-field"><Checkbox id="notification-unread" checked={unreadOnly} onCheckedChange={value => { setUnreadOnly(value === true); setPage(1); }} /><FieldLabel htmlFor="notification-unread">읽지 않은 알림만 보기</FieldLabel></Field>
      <Button variant="outline" disabled={!unreadCount} onClick={() => setAction({ title: "모든 알림 읽음으로 표시", description: "내 계정의 읽지 않은 알림을 모두 읽음으로 표시합니다.", submitLabel: "모두 읽음 처리", onSubmit: () => readAll.mutateAsync() })}>모두 읽음 처리</Button>
    </div>
    <QueryState query={query}>
      <DataTable rows={currentRows} empty={typeFilter !== "ALL" ? "선택한 유형의 알림이 없습니다" : unreadOnly ? "읽지 않은 알림이 없습니다" : "도착한 알림이 없습니다"} columns={[
        { label: "알림", render: notification => <div className={`admin-notification-summary${notification.read ? "" : " is-unread"}`}><span className="admin-notification-indicator" aria-hidden="true" /><div><strong>{notification.title}</strong><p>{notification.message}</p></div></div> },
        { label: "분류", render: notification => notificationCategories[notification.type] ?? "안내" },
        { label: "받은 시간", render: notification => <time dateTime={notification.createdAt}>{dateTime(notification.createdAt)}</time> },
        { label: "상태", render: notification => <StatusBadge domain="notification" status={notification.read ? "READ" : "UNREAD"} /> },
        { label: "확인", render: notification => <Button variant="ghost" aria-label={`${notification.title} 알림 상세 보기`} onClick={() => void detail.open(notification)}>상세 보기</Button> },
      ]} />
    </QueryState>
    <Pagination total={filtered.length} page={currentPage} totalPages={totalPages} onPageChange={setPage} />
    {action && <ActionDialog action={action} onClose={() => setAction(null)} />}
    {detail.dialog}
  </AdminPage>;
}

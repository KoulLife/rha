import { useState, type ReactNode } from "react";
import { Dialog } from "@/components/ui/Dialog";
import type { Screen } from "@/app/routes";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/components/ui/empty";
import { AttendanceCount, StatusBadge } from "@/components/ui/StatusBadge";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { getDashboardSnapshot, seoulDate, seoulTime, type DashboardData, type DashboardLesson, type DashboardNotification, type DashboardUser } from "./model";
import { dashboardPreviewTime } from "./preview";
import { MemberHomePage } from "./MemberHomePage";
import { ReservationRoster } from "./ReservationRoster";
import { NotificationsPage } from "@/features/member/pages/NotificationsPage";
import "./dashboard.css";

type Props = {
  user: DashboardUser; data: DashboardData; screen?: Screen;
  onNavigate: (screen: Screen, id?: number) => void;
  onReadNotification: (id: number) => void;
  empty?: boolean;
};

function DashboardPanel({ title, children, action, variant = "default" }: { title?: string; children: ReactNode; action?: ReactNode; variant?: "default" | "table" }) {
  return <Card className={`dashboard-panel${variant === "table" ? " dashboard-panel-table" : ""}`}>
    {title && <CardHeader className="dashboard-panel-heading"><CardTitle><h2>{title}</h2></CardTitle>{action}</CardHeader>}
    <CardContent>{children}</CardContent>
  </Card>;
}

function DashboardEmpty({ title, description }: { title: string; description: string }) {
  return <Empty className="dashboard-empty"><EmptyHeader><EmptyTitle>{title}</EmptyTitle><EmptyDescription>{description}</EmptyDescription></EmptyHeader></Empty>;
}

function NotificationDetail({ notification, onRead }: { notification: DashboardNotification; onRead: (id: number) => void }) {
  const [open, setOpen] = useState(false);
  return <>
    <Button variant="ghost" aria-label={`${notification.title} 알림 상세 보기`} onClick={() => setOpen(true)}>상세 보기</Button>
    {open && <Dialog dialog={{ title: notification.title, body: `${notification.message}\n\n${seoulDate(notification.createdAt)} ${seoulTime(notification.createdAt)}`, action: notification.read ? undefined : "읽음으로 표시" }} onClose={() => setOpen(false)} onConfirm={() => onRead(notification.id)} />}
  </>;
}

function NotificationList({ items, onRead }: { items: DashboardNotification[]; onRead: (id: number) => void }) {
  return items.length ? <ul className="dashboard-short-list dashboard-dated-list">{items.map(item => <li key={item.id}>
    <h3>{item.title}</h3>
    <time className="dashboard-list-date" dateTime={item.createdAt}><span>{seoulDate(item.createdAt)}</span><span>{seoulTime(item.createdAt)}</span></time>
    <div className="dashboard-row-actions"><StatusBadge domain="notification" status={item.read ? "READ" : "UNREAD"} /><NotificationDetail notification={item} onRead={onRead} /></div>
  </li>)}</ul> : <DashboardEmpty title="새로운 알림이 없습니다" description="새 알림이 도착하면 여기에서 확인할 수 있습니다." />;
}

export function DashboardPage({ user, data, screen = "home", onNavigate, onReadNotification, empty = false }: Props) {
  const snapshot = getDashboardSnapshot(data, user, dashboardPreviewTime);
  const member = user.role === "MEMBER";
  const admin = user.role === "ADMIN";
  const [selectedLessonId, setSelectedLessonId] = useState<number | null>(null);
  const selectedLesson = screen === "today-lessons" ? snapshot.todayLessons.find(item => item.id === selectedLessonId) : undefined;
  const lessonsScreen: Screen = member ? "reservations" : "today-lessons";
  const notificationAction = <Button variant="ghost" onClick={() => onNavigate("notifications")}>전체 보기</Button>;

  const lessonList = (items: DashboardLesson[], preview = false) => !items.length ? <DashboardEmpty title={member ? "오늘 내 수업이 없습니다" : "오늘 수업이 없습니다"} description={member ? "예약한 오늘 수업만 표시됩니다. 수업 목록에서 다음 수업을 예약해 보세요." : "오늘 예정된 수업이 등록되면 여기에서 확인할 수 있습니다."} /> : member ?
    <div className="dashboard-member-lessons">{items.map(lesson => {
      const reservation = snapshot.reservations.find(item => item.lessonId === lesson.id)!;
      return <Card key={lesson.id} className="dashboard-lesson-card"><CardContent>
        <div className="dashboard-time"><strong>{seoulTime(lesson.startAt)}</strong><span>{seoulTime(lesson.endAt)} 종료</span></div>
        <div className="dashboard-lesson-info"><h3>{lesson.title}</h3><p>{lesson.instructor} · {lesson.room}</p></div>
        <div className="dashboard-row-actions"><StatusBadge domain="reservation" status={reservation.status} /><Button variant="ghost" onClick={() => onNavigate("reservation-detail", reservation.id)}>예약 상세</Button></div>
      </CardContent></Card>;
    })}</div> : <Table variant="management" className="dashboard-lessons-table dashboard-lessons-schedule">
      <TableHeader><TableRow><TableHead>룸</TableHead><TableHead>시간</TableHead><TableHead>수업</TableHead><TableHead>강사</TableHead><TableHead>예약 / 정원</TableHead><TableHead>출결</TableHead><TableHead>상세</TableHead></TableRow></TableHeader>
      <TableBody>{(preview ? items.slice(0, 5) : items).map(lesson => {
        const roster = snapshot.reservations.filter(item => item.lessonId === lesson.id);
        const recorded = roster.filter(item => item.status === "ATTENDED" || item.status === "NO_SHOW").length;
        return <TableRow key={lesson.id} data-state={selectedLesson?.id === lesson.id ? "selected" : undefined}><TableCell>{lesson.room}</TableCell><TableCell>{seoulTime(lesson.startAt)}–{seoulTime(lesson.endAt)}</TableCell>
          <TableCell><strong>{lesson.title}</strong></TableCell><TableCell>{lesson.instructor}</TableCell><TableCell>{roster.length} / {lesson.capacity}명</TableCell>
          <TableCell>{recorded} / {roster.length}명</TableCell><TableCell><Button variant="outline" size="sm"
            onClick={() => { if (admin) { onNavigate("admin-lesson-detail", lesson.id); return; } setSelectedLessonId(lesson.id); if (screen !== "today-lessons") onNavigate("today-lessons"); }}
            aria-label={`${lesson.title} ${admin ? "예약자 보기" : "상세 보기"}`}
            aria-expanded={screen === "today-lessons" ? selectedLesson?.id === lesson.id : undefined}
            aria-controls={selectedLesson?.id === lesson.id ? "today-lesson-roster" : undefined}
          >{admin ? "예약자 보기" : "상세 보기"}</Button></TableCell></TableRow>;
      })}{preview && Array.from({ length: Math.max(0, 5 - items.length) }, (_, index) => <TableRow key={`empty-${index}`} aria-hidden="true" className="dashboard-lesson-placeholder"><TableCell colSpan={7} /></TableRow>)}</TableBody>
    </Table>;

  const absenceList = !snapshot.absences.length ? <DashboardEmpty title="승인 대기 중인 부재 신청이 없습니다" description={admin ? "새 부재 신청이 접수되면 표시됩니다." : "내 부재 신청의 승인 대기 상태를 확인할 수 있습니다."} /> :
    <ul className="dashboard-short-list dashboard-dated-list">{snapshot.absences.map(item => <li key={item.id}><h3>{item.instructor} · {item.reason}</h3><div className="dashboard-list-date"><time dateTime={item.from}>{item.from}</time>{item.from !== item.to && <time dateTime={item.to}>~ {item.to}</time>}</div><StatusBadge domain="absence" status={item.status} /></li>)}</ul>;

  if (screen === "notifications") return <NotificationsPage role={user.role} onNavigate={onNavigate} />;

  if (screen !== "home") {
    const titles = { "today-lessons": admin ? "오늘 전체 수업" : "오늘 담당 수업", "attendance-overview": "오늘 예약·출결 현황", "absence-requests": admin ? "부재 승인 대기" : "내 부재 승인 대기", notifications: "내 알림" };
    const title = titles[screen as keyof typeof titles] ?? "홈";
    return <div className="dashboard-page dashboard-subpage">
      <div className="dashboard-heading"><div><h1>{title}</h1></div><Button variant="outline" onClick={() => { setSelectedLessonId(null); onNavigate("home"); }}>홈으로</Button></div>
      {screen === "absence-requests" ? <DashboardPanel variant="table">
        {snapshot.absences.length ? <Table variant="management"><TableHeader><TableRow><TableHead>강사</TableHead><TableHead>부재 기간</TableHead><TableHead>사유</TableHead><TableHead>상태</TableHead></TableRow></TableHeader><TableBody>{snapshot.absences.map(item => <TableRow key={item.id}><TableCell>{item.instructor}</TableCell><TableCell>{item.from === item.to ? item.from : `${item.from}–${item.to}`}</TableCell><TableCell>{item.reason}</TableCell><TableCell><StatusBadge domain="absence" status={item.status} /></TableCell></TableRow>)}</TableBody></Table> : absenceList}
      </DashboardPanel> : <>
        {screen === "today-lessons" && <DashboardPanel variant="table">{lessonList(snapshot.todayLessons)}</DashboardPanel>}
        {(screen === "attendance-overview" || selectedLesson) && <section id="today-lesson-roster" aria-label={selectedLesson ? `${selectedLesson.title} 예약자·출결` : "오늘 예약·출결"}><DashboardPanel variant="table" title={selectedLesson ? `${selectedLesson.title} 예약자·출결` : undefined} action={selectedLesson ? <Button variant="ghost" onClick={() => setSelectedLessonId(null)}>선택 해제</Button> : undefined}>
          {snapshot.reservations.filter(item => !selectedLesson || item.lessonId === selectedLesson.id).length ? <ReservationRoster reservations={snapshot.reservations.filter(item => !selectedLesson || item.lessonId === selectedLesson.id)} lessons={snapshot.todayLessons} memberActionLabel={screen === "today-lessons" ? "회원정보 보기" : "회원 상세"} /> : <DashboardEmpty title="예약자가 없습니다" description="선택한 범위에 예약된 회원이 없습니다." />}
        </DashboardPanel></section>}
      </>}
    </div>;
  }

  if (member) return <MemberHomePage user={user} data={data} onNavigate={onNavigate} empty={empty} />;

  const metrics = [
    { label: admin ? "오늘 전체 수업" : "오늘 담당 수업", value: `${snapshot.todayLessons.length}개`, target: lessonsScreen },
    { label: "오늘 예약", value: `${snapshot.reservations.length}명`, attendance: true, target: "attendance-overview" as const },
    { label: admin ? "부재 승인 대기" : "출결 처리 대기", value: `${admin ? snapshot.absences.length : snapshot.attendancePending}건`, target: "absence-requests" as const },
    { label: "읽지 않은 알림", value: `${snapshot.unread}건`, target: "notifications" as const },
  ];
  return <div className="dashboard-page">
    <div className="dashboard-heading"><h1>{admin ? "오늘의 센터 운영" : "오늘의 담당 수업"}</h1></div>
    <section className="dashboard-metrics" aria-label="오늘 현황 요약">{metrics.map(item => <Card key={item.label} className="dashboard-metric"><CardContent><Button variant="ghost" className="dashboard-metric-link" onClick={() => onNavigate(item.target)}><span>{item.label}</span>
      {item.attendance ? <span className="dashboard-reservation-value"><strong>{item.value}</strong><span className="dashboard-reservation-counts"><AttendanceCount status="ATTENDED" count={snapshot.attended} /><AttendanceCount status="NO_SHOW" count={snapshot.noShow} /></span></span> : <strong>{item.value}</strong>}
    </Button></CardContent></Card>)}</section>
    <div className="dashboard-columns">
      <div className="dashboard-column">
        <DashboardPanel title={admin ? "오늘 전체 수업" : "오늘 담당 수업"} variant="table" action={<Button variant="ghost" onClick={() => onNavigate(lessonsScreen)}>전체 보기</Button>}>{lessonList(snapshot.todayLessons, true)}</DashboardPanel>
        <DashboardPanel title={admin ? "부재 승인 대기" : "내 부재 승인 대기"} action={<Button variant="ghost" onClick={() => onNavigate("absence-requests")}>전체 보기</Button>}>{absenceList}</DashboardPanel>
      </div>
      <div className="dashboard-column">
        <DashboardPanel title="최근 알림" action={notificationAction}><NotificationList items={snapshot.notifications.slice(0, 6)} onRead={onReadNotification} /></DashboardPanel>
      </div>
    </div>
  </div>;
}

import { useState, type ReactNode } from "react";
import { Eye } from "lucide-react";
import * as api from "@/api/generated/api";
import { PaginatedList } from "@/components/ui/PaginatedList";
import type { Screen } from "@/app/routes";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/components/ui/empty";
import { AttendanceCount, StatusBadge } from "@/components/ui/StatusBadge";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { getDashboardSnapshot, seoulDate, seoulTime, type DashboardData, type DashboardLesson, type DashboardUser } from "./model";
import { dashboardPreviewTime } from "./preview";
import { MemberHomePage } from "./MemberHomePage";
import { ReservationRoster } from "./ReservationRoster";
import { NotificationsPage } from "@/features/member/pages/NotificationsPage";
import { useNotificationDetail } from "@/features/member/pages/NotificationDetail";
import { QueryState } from "@/features/admin/AdminUI";
import { AttendanceActions, type RecordAttendance } from "../instructor/AttendanceActions";
import { attendanceRestriction } from "../instructor/model";
import "./dashboard.css";

type Props = {
  user: DashboardUser; data: DashboardData; screen?: Screen;
  onNavigate: (screen: Screen, id?: number) => void;
  empty?: boolean;
  onRecordAttendance?: RecordAttendance;
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

function NotificationList({ items, detail }: { items: api.Notification[]; detail: ReturnType<typeof useNotificationDetail> }) {
  return items.length ? <div className="dashboard-notification-scroll" role="region" aria-label="최근 알림 목록" tabIndex={0}><ul className="dashboard-short-list dashboard-dated-list">{items.map(item => <li key={item.id}>
    <h3>{item.title}</h3>
    <time className="dashboard-list-date" dateTime={item.createdAt}><span>{seoulDate(item.createdAt)}</span><span>{seoulTime(item.createdAt)}</span></time>
    <div className="dashboard-row-actions"><StatusBadge domain="notification" status={item.read ? "READ" : "UNREAD"} /><Button variant="outline" size="icon-sm" aria-label={`${item.title} 알림 바로 보기`} title="알림 바로 보기" onClick={() => void detail.open(item)}><Eye aria-hidden="true" /></Button></div>
  </li>)}</ul></div> : <DashboardEmpty title="새로운 알림이 없습니다" description="새 알림이 도착하면 여기에서 확인할 수 있습니다." />;
}

export function DashboardPage({ user, data, screen = "home", onNavigate, onRecordAttendance, empty = false }: Props) {
  const snapshot = getDashboardSnapshot(data, user, dashboardPreviewTime);
  const notificationQuery = api.useGetMyNotifications({ page: 1, size: 100 }, { query: { enabled: screen === "home" && user.role !== "MEMBER" && !empty } });
  const unreadQuery = api.useGetMyUnreadNotificationCount({ query: { enabled: screen === "home" && user.role !== "MEMBER" && !empty } });
  const detail = useNotificationDetail(user.role, onNavigate);
  const recentNotifications = empty ? [] : (notificationQuery.data?.content ?? []);
  const member = user.role === "MEMBER";
  const admin = user.role === "ADMIN";
  const [selectedLessonId, setSelectedLessonId] = useState<number | null>(null);
  const selectedLesson = screen === "today-lessons" ? snapshot.todayLessons.find(item => item.id === selectedLessonId) : undefined;
  const lessonsScreen: Screen = member ? "reservations" : "today-lessons";
  const notificationAction = <Button variant="ghost" onClick={() => onNavigate("notifications")}>전체 보기</Button>;

  const lessonTable = (pageItems: DashboardLesson[]) => <Table variant="management" className="dashboard-lessons-table dashboard-lessons-schedule">
    <TableHeader><TableRow><TableHead>룸</TableHead><TableHead>시간</TableHead><TableHead>수업</TableHead><TableHead>강사</TableHead><TableHead>예약 / 정원</TableHead><TableHead>출결</TableHead><TableHead>상세</TableHead></TableRow></TableHeader>
    <TableBody>{pageItems.map(lesson => {
      const roster = snapshot.reservations.filter(item => item.lessonId === lesson.id);
      const recorded = roster.filter(item => item.status === "ATTENDED" || item.status === "NO_SHOW").length;
      return <TableRow key={lesson.id} data-state={selectedLesson?.id === lesson.id ? "selected" : undefined}><TableCell>{lesson.room}</TableCell><TableCell>{seoulTime(lesson.startAt)}–{seoulTime(lesson.endAt)}</TableCell>
        <TableCell><strong>{lesson.title}</strong></TableCell><TableCell>{lesson.instructor}</TableCell><TableCell>{roster.length} / {lesson.capacity}명</TableCell>
        <TableCell>{recorded} / {roster.length}명</TableCell><TableCell><Button variant="outline" size="table-action"
          onClick={() => { if (admin) { onNavigate("admin-lesson-detail", lesson.id); return; } onNavigate("timetable", lesson.id); }}
          aria-label={`${lesson.title} ${admin ? "예약자 보기" : "상세 보기"}`}
          aria-expanded={screen === "today-lessons" ? selectedLesson?.id === lesson.id : undefined}
          aria-controls={selectedLesson?.id === lesson.id ? "today-lesson-roster" : undefined}
        >{admin ? "예약자 보기" : "상세 보기"}</Button></TableCell></TableRow>;
    })}</TableBody>
  </Table>;

  const lessonList = (items: DashboardLesson[], preview = false) => !items.length ? <DashboardEmpty title={member ? "오늘 내 수업이 없습니다" : "오늘 수업이 없습니다"} description={member ? "예약한 오늘 수업만 표시됩니다. 수업 목록에서 다음 수업을 예약해 보세요." : "오늘 예정된 수업이 등록되면 여기에서 확인할 수 있습니다."} /> : member ?
    <div className="dashboard-member-lessons">{items.map(lesson => {
      const reservation = snapshot.reservations.find(item => item.lessonId === lesson.id)!;
      return <Card key={lesson.id} className="dashboard-lesson-card"><CardContent>
        <div className="dashboard-time"><strong>{seoulTime(lesson.startAt)}</strong><span>{seoulTime(lesson.endAt)} 종료</span></div>
        <div className="dashboard-lesson-info"><h3>{lesson.title}</h3><p>{lesson.instructor} · {lesson.room}</p></div>
        <div className="dashboard-row-actions"><StatusBadge domain="reservation" status={reservation.status} /><Button variant="ghost" onClick={() => onNavigate("reservation-detail", reservation.id)}>예약 상세</Button></div>
      </CardContent></Card>;
    })}</div> : preview ? lessonTable(items) : <PaginatedList items={items}>{lessonTable}</PaginatedList>;

  const absenceRows = (items: typeof snapshot.absences) => <ul className="dashboard-short-list dashboard-dated-list">{items.map(item => <li key={item.id}><h3>{item.instructor} · {item.reason}</h3><div className="dashboard-list-date"><time dateTime={item.from}>{item.from}</time>{item.from !== item.to && <time dateTime={item.to}>~ {item.to}</time>}</div><StatusBadge domain="absence" status={item.status} /></li>)}</ul>;
  const absenceList = !snapshot.absences.length ? <DashboardEmpty title="승인 대기 중인 부재 신청이 없습니다" description={admin ? "새 부재 신청이 접수되면 표시됩니다." : "내 부재 신청의 승인 대기 상태를 확인할 수 있습니다."} /> : screen === "home" ? absenceRows(snapshot.absences) :
    <PaginatedList items={snapshot.absences} alwaysShowPagination={!admin}>{absenceRows}</PaginatedList>;

  if (screen === "notifications") return <NotificationsPage role={user.role} onNavigate={onNavigate} />;

  if (screen !== "home") {
    const titles = { "today-lessons": admin ? "오늘 전체 수업" : "오늘 담당 수업", "attendance-overview": "오늘 예약·출결 현황", "absence-requests": admin ? "부재 승인 대기" : "내 부재 승인 대기", notifications: "내 알림" };
    const title = titles[screen as keyof typeof titles] ?? "홈";
    return <div className="dashboard-page dashboard-subpage">
      <div className="dashboard-heading"><div><h1>{title}</h1></div><Button variant="outline" onClick={() => { setSelectedLessonId(null); onNavigate("home"); }}>홈으로</Button></div>
      {screen === "absence-requests" ? <DashboardPanel variant="table">
        {snapshot.absences.length ? <PaginatedList items={snapshot.absences}>{pageItems => <Table variant="management"><TableHeader><TableRow><TableHead>강사</TableHead><TableHead>부재 기간</TableHead><TableHead>사유</TableHead><TableHead>상태</TableHead></TableRow></TableHeader><TableBody>{pageItems.map(item => <TableRow key={item.id}><TableCell>{item.instructor}</TableCell><TableCell>{item.from === item.to ? item.from : `${item.from}–${item.to}`}</TableCell><TableCell>{item.reason}</TableCell><TableCell><StatusBadge domain="absence" status={item.status} /></TableCell></TableRow>)}</TableBody></Table>}</PaginatedList> : absenceList}
      </DashboardPanel> : <>
        {screen === "today-lessons" && <DashboardPanel variant="table">{lessonList(snapshot.todayLessons)}</DashboardPanel>}
        {(screen === "attendance-overview" || selectedLesson) && <section id="today-lesson-roster" aria-label={selectedLesson ? `${selectedLesson.title} 예약자·출결` : "오늘 예약·출결"}><DashboardPanel variant="table" title={selectedLesson ? `${selectedLesson.title} 예약자·출결` : undefined} action={selectedLesson ? <Button variant="ghost" onClick={() => setSelectedLessonId(null)}>선택 해제</Button> : undefined}>
          {snapshot.reservations.filter(item => !selectedLesson || item.lessonId === selectedLesson.id).length ? <ReservationRoster renderAttendance={user.role === "INSTRUCTOR" && onRecordAttendance ? reservation => <AttendanceActions reservation={reservation} restriction={attendanceRestriction(data, user, reservation.id, dashboardPreviewTime)} onRecord={onRecordAttendance} /> : undefined} reservations={snapshot.reservations.filter(item => !selectedLesson || item.lessonId === selectedLesson.id)} lessons={snapshot.todayLessons} memberActionLabel={screen === "today-lessons" ? "회원정보 보기" : "회원 상세"} /> : <DashboardEmpty title="예약자가 없습니다" description="선택한 범위에 예약된 회원이 없습니다." />}
        </DashboardPanel></section>}
      </>}
    </div>;
  }

  if (member) return <MemberHomePage user={user} data={data} onNavigate={onNavigate} empty={empty} />;

  const metrics = [
    { label: admin ? "오늘 전체 수업" : "오늘 담당 수업", value: `${snapshot.todayLessons.length}개`, target: lessonsScreen, id: admin ? undefined : snapshot.todayLessons[0]?.id },
    { label: "오늘 예약", value: `${snapshot.reservations.length}명`, attendance: true, target: "attendance-overview" as const, id: admin ? undefined : snapshot.todayLessons[0]?.id },
    { label: admin ? "부재 승인 대기" : "출결 처리 대기", value: `${admin ? snapshot.absences.length : snapshot.attendancePending}건`, target: (admin ? "absence-requests" : "attendance") as Screen, id: admin ? undefined : snapshot.todayLessons.find(lesson => snapshot.reservations.some(reservation => reservation.lessonId === lesson.id && reservation.status === "RESERVED" && Date.parse(lesson.endAt) <= Date.parse(dashboardPreviewTime)))?.id },
    { label: "읽지 않은 알림", value: `${empty ? 0 : unreadQuery.data?.unreadCount ?? 0}건`, target: "notifications" as const },
  ];
  return <div className="dashboard-page">
    <div className="dashboard-heading"><h1>{admin ? "오늘의 센터 운영" : "오늘의 담당 수업"}</h1></div>
    <section className="dashboard-metrics" aria-label="오늘 현황 요약">{metrics.map(item => <Card key={item.label} className="dashboard-metric"><CardContent><Button variant="ghost" className="dashboard-metric-link" onClick={() => onNavigate(item.target, item.id)}><span>{item.label}</span>
      {item.attendance ? <span className="dashboard-reservation-value"><strong>{item.value}</strong><span className="dashboard-reservation-counts"><AttendanceCount status="ATTENDED" count={snapshot.attended} /><AttendanceCount status="NO_SHOW" count={snapshot.noShow} /></span></span> : <strong>{item.value}</strong>}
    </Button></CardContent></Card>)}</section>
    <div className="dashboard-columns">
      <DashboardPanel title={admin ? "오늘 전체 수업" : "오늘 담당 수업"} variant="table" action={<Button variant="ghost" onClick={() => onNavigate(lessonsScreen)}>전체 보기</Button>}>{lessonList(snapshot.todayLessons, true)}</DashboardPanel>
      <div className="dashboard-side">
        <DashboardPanel title={admin ? "부재 승인 대기" : "내 부재 승인 대기"} action={<Button variant="ghost" onClick={() => onNavigate("absence-requests")}>전체 보기</Button>}>{absenceList}</DashboardPanel>
        <DashboardPanel title="최근 알림" action={notificationAction}>{empty ? <NotificationList items={[]} detail={detail} /> : <QueryState query={notificationQuery}><NotificationList items={recentNotifications} detail={detail} /></QueryState>}</DashboardPanel>
      </div>
    </div>
    {detail.dialog}
  </div>;
}

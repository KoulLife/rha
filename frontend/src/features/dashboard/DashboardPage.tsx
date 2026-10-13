import { useState, type ReactNode } from "react";
import { Dialog } from "@/components/ui/Dialog";
import type { Screen } from "@/app/routes";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/components/ui/empty";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { getDashboardSnapshot, roleLabels, seoulDate, seoulTime, type DashboardData, type DashboardLesson, type DashboardNotification, type DashboardUser } from "./model";
import { dashboardPreviewTime } from "./preview";
import { MemberHomePage } from "./MemberHomePage";
import { NotificationsPage } from "@/features/member/pages/NotificationsPage";

type Props = {
  user: DashboardUser; data: DashboardData; screen?: Screen;
  onNavigate: (screen: Screen, id?: number) => void;
  onReadNotification: (id: number) => void;
  empty?: boolean;
};

function DashboardPanel({ title, children, action }: { title: string; children: ReactNode; action?: ReactNode }) {
  return <Card className="dashboard-panel">
    <CardHeader className="dashboard-panel-heading"><CardTitle><h2>{title}</h2></CardTitle>{action}</CardHeader>
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
  return items.length ? <ul className="dashboard-short-list">{items.map(item => <li key={item.id}>
    <div><h3>{item.title}</h3><p>{seoulDate(item.createdAt)} {seoulTime(item.createdAt)}</p></div>
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

  const lessonList = (items: DashboardLesson[]) => !items.length ? <DashboardEmpty title={member ? "오늘 내 수업이 없습니다" : "오늘 수업이 없습니다"} description={member ? "예약한 오늘 수업만 표시됩니다. 수업 목록에서 다음 수업을 예약해 보세요." : "오늘 예정된 수업이 등록되면 여기에서 확인할 수 있습니다."} /> : member ?
    <div className="dashboard-member-lessons">{items.map(lesson => {
      const reservation = snapshot.reservations.find(item => item.lessonId === lesson.id)!;
      return <Card key={lesson.id} className="dashboard-lesson-card"><CardContent>
        <div className="dashboard-time"><strong>{seoulTime(lesson.startAt)}</strong><span>{seoulTime(lesson.endAt)} 종료</span></div>
        <div className="dashboard-lesson-info"><h3>{lesson.title}</h3><p>{lesson.instructor} · {lesson.room}</p></div>
        <div className="dashboard-row-actions"><StatusBadge domain="reservation" status={reservation.status} /><Button variant="ghost" onClick={() => onNavigate("reservation-detail", reservation.id)}>예약 상세</Button></div>
      </CardContent></Card>;
    })}</div> : <Table>
      <TableHeader><TableRow><TableHead>시간</TableHead><TableHead>수업 / 강사</TableHead><TableHead>예약 / 정원</TableHead><TableHead>출결</TableHead><TableHead>상세</TableHead></TableRow></TableHeader>
      <TableBody>{items.map(lesson => {
        const roster = snapshot.reservations.filter(item => item.lessonId === lesson.id);
        const recorded = roster.filter(item => item.status === "ATTENDED" || item.status === "NO_SHOW").length;
        return <TableRow key={lesson.id}><TableCell>{seoulTime(lesson.startAt)}–{seoulTime(lesson.endAt)}<p className="dashboard-muted">{lesson.room}</p></TableCell>
          <TableCell><strong>{lesson.title}</strong><p className="dashboard-muted">{lesson.instructor}</p></TableCell><TableCell>{roster.length} / {lesson.capacity}명</TableCell>
          <TableCell>{recorded} / {roster.length}명 기록</TableCell><TableCell><Button variant="ghost" onClick={() => { setSelectedLessonId(lesson.id); onNavigate("today-lessons"); }} aria-label={`${lesson.title} 예약자 보기`}>예약자 보기</Button></TableCell></TableRow>;
      })}</TableBody>
    </Table>;

  const absenceList = !snapshot.absences.length ? <DashboardEmpty title="승인 대기 중인 부재 신청이 없습니다" description={admin ? "새 부재 신청이 접수되면 표시됩니다." : "내 부재 신청의 승인 대기 상태를 확인할 수 있습니다."} /> :
    <ul className="dashboard-short-list">{snapshot.absences.map(item => <li key={item.id}><div><h3>{item.instructor} · {item.reason}</h3><p>{item.from === item.to ? item.from : `${item.from}–${item.to}`}</p></div><StatusBadge domain="absence" status={item.status} /></li>)}</ul>;

  if (screen === "notifications") return <NotificationsPage items={snapshot.notifications} onRead={onReadNotification} />;

  if (screen !== "home") {
    const titles = { "today-lessons": admin ? "오늘 전체 수업" : "오늘 담당 수업", "attendance-overview": "오늘 예약·출결 현황", "absence-requests": admin ? "부재 승인 대기" : "내 부재 승인 대기", notifications: "내 알림" };
    const title = titles[screen as keyof typeof titles] ?? "홈";
    return <div className="dashboard-page dashboard-subpage">
      <div className="dashboard-heading"><div><h1>{title}</h1><p>예시 기준 · {seoulDate(dashboardPreviewTime)} {seoulTime(dashboardPreviewTime)} · 서울</p></div><Button variant="outline" onClick={() => { setSelectedLessonId(null); onNavigate("home"); }}>홈으로</Button></div>
      {screen === "absence-requests" ? <DashboardPanel title={title}>
        {snapshot.absences.length ? <Table><TableHeader><TableRow><TableHead>강사</TableHead><TableHead>부재 기간</TableHead><TableHead>사유</TableHead><TableHead>상태</TableHead></TableRow></TableHeader><TableBody>{snapshot.absences.map(item => <TableRow key={item.id}><TableCell>{item.instructor}</TableCell><TableCell>{item.from === item.to ? item.from : `${item.from}–${item.to}`}</TableCell><TableCell>{item.reason}</TableCell><TableCell><StatusBadge domain="absence" status={item.status} /></TableCell></TableRow>)}</TableBody></Table> : absenceList}
      </DashboardPanel> : <>
        {screen === "today-lessons" && <DashboardPanel title={title}>{lessonList(snapshot.todayLessons)}</DashboardPanel>}
        {(screen === "attendance-overview" || selectedLesson) && <DashboardPanel title={selectedLesson ? `${selectedLesson.title} 예약자·출결` : "오늘 예약자·출결"} action={selectedLesson ? <Button variant="ghost" onClick={() => setSelectedLessonId(null)}>선택 해제</Button> : undefined}>
          <p className="dashboard-section-note">출결 상태를 확인하는 화면입니다. 예시 데이터는 변경되지 않습니다.</p>
          {snapshot.reservations.filter(item => !selectedLesson || item.lessonId === selectedLesson.id).length ? <Table><TableHeader><TableRow><TableHead>회원</TableHead><TableHead>수업</TableHead><TableHead>시간</TableHead><TableHead>출결 상태</TableHead></TableRow></TableHeader><TableBody>{snapshot.reservations.filter(item => !selectedLesson || item.lessonId === selectedLesson.id).map(item => {
            const lesson = snapshot.todayLessons.find(lesson => lesson.id === item.lessonId)!;
            return <TableRow key={item.id}><TableCell>{item.member}</TableCell><TableCell>{lesson.title}</TableCell><TableCell>{seoulTime(lesson.startAt)}</TableCell><TableCell><StatusBadge domain="reservation" status={item.status} /></TableCell></TableRow>;
          })}</TableBody></Table> : <DashboardEmpty title="예약자가 없습니다" description="선택한 범위에 예약된 회원이 없습니다." />}
        </DashboardPanel>}
      </>}
    </div>;
  }

  if (member) return <MemberHomePage user={user} data={data} onNavigate={onNavigate} empty={empty} />;

  const metrics = [
    { label: admin ? "오늘 전체 수업" : "오늘 담당 수업", value: `${snapshot.todayLessons.length}개`, detail: admin ? "센터 전체 수업" : "내 담당 수업만", target: lessonsScreen },
    { label: "오늘 예약", value: `${snapshot.reservations.length}명`, detail: `출석 ${snapshot.attended} · 노쇼 ${snapshot.noShow}`, target: "attendance-overview" as const },
    { label: admin ? "부재 승인 대기" : "출결 처리 대기", value: `${admin ? snapshot.absences.length : snapshot.attendancePending}건`, detail: admin ? "강사 부재 신청" : "종료된 수업의 미처리 예약", target: admin ? "absence-requests" as const : "attendance-overview" as const },
    { label: "읽지 않은 알림", value: `${snapshot.unread}건`, detail: "내 계정으로 받은 알림", target: "notifications" as const },
  ];
  return <div className="dashboard-page">
    <div className="dashboard-heading"><div><div className="dashboard-eyebrow">{roleLabels[user.role]} 홈</div><h1>{admin ? "오늘의 센터 운영" : "오늘의 담당 수업"}</h1><p>{user.name}님, {admin ? "센터의 수업과 처리할 업무를 확인하세요." : "담당 수업의 예약자와 출결 상태를 확인하세요."}</p></div>
      <div className="dashboard-heading-actions"><span>예시 기준 · {seoulDate(dashboardPreviewTime)} {seoulTime(dashboardPreviewTime)}</span></div>
    </div>
    <section className="dashboard-metrics" aria-label="오늘 현황 요약">{metrics.map(item => <Card key={item.label} className="dashboard-metric"><CardContent><Button variant="ghost" className="dashboard-metric-link" onClick={() => onNavigate(item.target)}><span>{item.label}</span><strong>{item.value}</strong><small>{item.detail}</small></Button></CardContent></Card>)}</section>
    <div className="dashboard-columns">
      <div className="dashboard-column">
        <DashboardPanel title={admin ? "오늘 전체 수업" : "오늘 담당 수업"} action={<Button variant="ghost" onClick={() => onNavigate(lessonsScreen)}>전체 보기</Button>}>{lessonList(snapshot.todayLessons)}</DashboardPanel>
        <DashboardPanel title="오늘 예약·출결 현황" action={<Button variant="ghost" onClick={() => onNavigate("attendance-overview")}>내역 보기</Button>}>
          <dl className="dashboard-attendance">{[["예약", snapshot.reservations.length], ["출석", snapshot.attended], ["노쇼", snapshot.noShow], ["출결 처리 대기", snapshot.attendancePending]].map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}<span>명</span></dd></div>)}</dl>
          <p className="dashboard-section-note">출결 처리 대기는 종료된 수업의 미처리 예약만 집계합니다.</p>
        </DashboardPanel>
      </div>
      <div className="dashboard-column">
        <DashboardPanel title={admin ? "부재 승인 대기" : "내 부재 승인 대기"} action={<Button variant="ghost" onClick={() => onNavigate("absence-requests")}>전체 보기</Button>}>{absenceList}</DashboardPanel>
        <DashboardPanel title="최근 알림" action={notificationAction}><NotificationList items={snapshot.notifications.slice(0, 3)} onRead={onReadNotification} /></DashboardPanel>
      </div>
    </div>
  </div>;
}

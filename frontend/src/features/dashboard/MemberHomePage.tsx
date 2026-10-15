import type { ReactNode } from "react";
import { cva } from "class-variance-authority";
import type { Screen } from "@/app/routes";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/components/ui/empty";
import { PAGE_SIZE } from "@/components/ui/Pagination";
import { PaginatedList } from "@/components/ui/PaginatedList";
import { Progress } from "@/components/ui/progress";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { lessons, passes } from "@/mocks/member";
import { seoulDate, seoulTime, type DashboardData, type DashboardUser } from "./model";
import "./member-home.css";

const weekdayFormatter = new Intl.DateTimeFormat("ko-KR", { timeZone: "Asia/Seoul", weekday: "short" });

const panelVariants = cva("home-panel", { variants: { variant: {
  public: "home-panel-public", reservations: "home-panel-reservations", pass: "home-panel-pass",
} } });

function HomePanel({ title, action, children, variant }: { title: string; action: ReactNode; children: ReactNode; variant: "public" | "reservations" | "pass" }) {
  return <Card size="sm" className={panelVariants({ variant })}>
    <CardHeader className="home-panel-heading"><CardTitle><h2>{title}</h2></CardTitle>{action}</CardHeader>
    <CardContent>{children}</CardContent>
  </Card>;
}

export function MemberHomePage({ user, data, onNavigate, empty = false }: {
  user: DashboardUser; data: DashboardData; onNavigate: (screen: Screen, id?: number) => void;
  empty?: boolean;
}) {
  const pass = !empty && user.id === 1 ? passes[0] : undefined;
  const publicLessons = empty ? [] : lessons;
  const ownReservations = data.reservations.filter(item => item.memberId === user.id);
  const reserved = ownReservations.filter(item => item.status === "RESERVED");
  const usedCount = pass ? pass.total - pass.remaining : 0;
  const metrics: { label: string; value: string; detail: string; target: Screen }[] = [
    { label: "예약 완료", value: String(reserved.length), detail: "내 예약 내역", target: "reservations" },
    { label: "대기 신청", value: "0", detail: "내 대기 내역", target: "reservations" },
    { label: "이용권 현재 잔여", value: `${pass?.remaining ?? 0}회`, detail: pass?.name ?? "보유 이용권 없음", target: "passes" },
    { label: "지난 수업·취소 기록", value: String(ownReservations.length - reserved.length), detail: "내 출결 기록", target: "reservations" },
  ];

  return <div className="home-page">
    <div className="home-heading">
      <div><h1>내 수업과 신청 현황</h1></div>
      <div className="home-heading-actions"><Button variant="outline" onClick={() => onNavigate("classes")}>수업 목록 보기</Button></div>
    </div>
    <section className="home-metrics" aria-label="내 현황 요약">
      {metrics.map(item => <Card size="sm" className="home-metric" key={item.label}><CardContent>
        <Button variant="ghost" className="home-metric-link" onClick={() => onNavigate(item.target)} aria-label={`${item.label} 내역 확인`}>
          <span className="home-metric-label">{item.label}</span><strong>{item.value}</strong><span className="home-metric-detail">{item.detail}</span>
        </Button>
      </CardContent></Card>)}
    </section>
    <div className="home-columns">
      <HomePanel variant="public" title="공개 수업 목록" action={<Button variant="ghost" onClick={() => onNavigate("classes")}>전체 보기</Button>}>
        {publicLessons.length ? <PaginatedList items={publicLessons}>{pageLessons => <Table variant="management" className="home-public-table" aria-label="공개 수업 목록">
          <TableHeader><TableRow>{["날짜", "시간", "수업명", "담당 강사", "룸", "예약 / 정원", "예약 상태", "상세"].map(label => <TableHead key={label} scope="col">{label}</TableHead>)}</TableRow></TableHeader>
          <TableBody>{pageLessons.map(lesson => <TableRow key={lesson.id}>
            <TableCell>{lesson.date} ({weekdayFormatter.format(new Date(lesson.startAt))})</TableCell>
            <TableCell>{lesson.time}</TableCell>
            <TableCell><strong>{lesson.type}</strong></TableCell>
            <TableCell>{lesson.teacher}</TableCell>
            <TableCell>{lesson.room}</TableCell>
            <TableCell>{lesson.booked}</TableCell>
            <TableCell><StatusBadge domain="lesson" status={lesson.status === "예약 가능" ? "AVAILABLE" : lesson.status === "대기 신청 가능" ? "WAITING" : "CLOSED"} /></TableCell>
            <TableCell><Button variant="outline" size="sm" onClick={() => onNavigate("lesson-detail", lesson.id)} aria-label={`${lesson.type} 상세 보기`}>상세 보기</Button></TableCell>
          </TableRow>)}{Array.from({ length: PAGE_SIZE - pageLessons.length }, (_, index) => <TableRow key={`public-empty-${index}`} aria-hidden="true"><TableCell colSpan={8} /></TableRow>)}</TableBody>
        </Table>}</PaginatedList> : <Empty><EmptyHeader><EmptyTitle>공개된 수업이 없습니다</EmptyTitle><EmptyDescription>새 수업이 공개되면 이곳에서 확인할 수 있습니다.</EmptyDescription></EmptyHeader></Empty>}

      </HomePanel>
      <div className="home-side">
        <HomePanel variant="reservations" title="내가 예약한 수업" action={<Button variant="ghost" onClick={() => onNavigate("reservations")}>전체 보기</Button>}>
          <PaginatedList items={reserved} pageSize={3} alwaysShowPagination>{pageReservations => <div className="home-reservation-list">{Array.from({ length: 3 }, (_, index) => {
            const item = pageReservations[index];
            if (!item) return <div className="home-reservation" key={`reservation-empty-${index}`} aria-hidden="true" />;
            const lesson = data.lessons.find(lesson => lesson.id === item.lessonId);
            return <div className="home-reservation" key={item.id}>
              <div><h3>{lesson?.title ?? "예약한 수업"}</h3>{lesson && <p>{seoulDate(lesson.startAt).replace(/-/g, ".")} {seoulTime(lesson.startAt)} · {lesson.instructor}</p>}</div>
              <div className="home-reservation-action"><StatusBadge domain="reservation" status="RESERVED" /><Button variant="ghost" onClick={() => onNavigate("reservation-detail", item.id)}>내역 보기</Button></div>
            </div>;
          })}</div>}</PaginatedList>
        </HomePanel>
        <HomePanel variant="pass" title="내 이용권" action={<Button variant="ghost" onClick={() => pass ? onNavigate("pass-detail", pass.id) : onNavigate("passes")}>상세 보기</Button>}>
          {pass ? <>
            <div className="home-pass-heading"><h3>{pass.name}</h3></div>
            <div className="home-pass-balance">
              <p className="home-pass-total">총 {pass.total}회 중</p>
              <p className="home-pass-remaining"><strong>{pass.remaining}<span>회</span></strong><span>남음</span></p>
              <Progress value={pass.total > 0 ? usedCount / pass.total * 100 : 0} aria-label={`총 ${pass.total}회 중 사용 ${usedCount}회, 남은 ${pass.remaining}회`} />
              <div className="home-pass-legend" aria-label="이용권 그래프 범례">
                <span><i className="home-pass-legend-used" aria-hidden="true" />사용 {usedCount}회</span>
                <span><i className="home-pass-legend-remaining" aria-hidden="true" />남음 {pass.remaining}회</span>
              </div>
            </div>
            <Table className="home-pass-count-table" aria-label="이용권 횟수 요약">
              <TableHeader><TableRow><TableHead scope="col">총 횟수</TableHead><TableHead scope="col">사용 횟수</TableHead><TableHead scope="col">남은 횟수</TableHead></TableRow></TableHeader>
              <TableBody><TableRow><TableCell>{pass.total}회</TableCell><TableCell>{usedCount}회</TableCell><TableCell><strong>{pass.remaining}회</strong></TableCell></TableRow></TableBody>
            </Table>
            <div className="home-pass-footer"><p>{pass.end.replace(/\./g, "-")} 만료</p><StatusBadge domain="pass" status="ACTIVE" /></div>
          </> : <Empty><EmptyHeader><EmptyTitle>보유 이용권이 없습니다</EmptyTitle><EmptyDescription>이용권이 발급되면 잔여 횟수와 만료일을 확인할 수 있습니다.</EmptyDescription></EmptyHeader></Empty>}
        </HomePanel>
      </div>
    </div>
  </div>;
}

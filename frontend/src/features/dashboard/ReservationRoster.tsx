import { useState } from "react";
import { Button } from "@/components/ui/button";
import { SortHeaderButton } from "@/components/ui/SortHeaderButton";
import { Card, CardContent } from "@/components/ui/card";
import { Dialog } from "@/components/ui/Dialog";
import { getStatusLabel, StatusBadge } from "@/components/ui/StatusBadge";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { seoulDate, seoulTime, type DashboardLesson, type DashboardReservation } from "./model";

function MemberInfoButton({ reservation, lesson, label }: { reservation: DashboardReservation; lesson: DashboardLesson; label: string }) {
  const [open, setOpen] = useState(false);
  return <>
    <Button variant="outline" size="sm" aria-label={`${reservation.member} ${label}`} onClick={() => setOpen(true)}>{label}</Button>
    {open && <Dialog dialog={{ title: "회원정보", body: `${reservation.member}님의 기본 정보와 선택한 수업의 예약 정보입니다.` }} onClose={() => setOpen(false)}>
      <div className="member-info">
        <Card className="member-info-summary"><CardContent>
          <div><p className="member-info-label">회원</p><h3>{reservation.member}</h3></div>
          <div className="member-info-number"><span>회원 번호</span><strong>#{reservation.memberId}</strong></div>
        </CardContent></Card>
        <section className="member-info-booking" aria-label="선택한 예약 정보">
          <div className="member-info-heading"><h3>예약 정보</h3><StatusBadge domain="reservation" status={reservation.status} /></div>
          <dl className="member-info-facts">
            <div><dt>수업</dt><dd>{lesson.title}</dd></div>
            <div><dt>수업일</dt><dd>{seoulDate(lesson.startAt)}</dd></div>
            <div><dt>시간</dt><dd>{seoulTime(lesson.startAt)}–{seoulTime(lesson.endAt)}</dd></div>
            <div><dt>담당 강사</dt><dd>{lesson.instructor}</dd></div>
            <div><dt>룸</dt><dd>{lesson.room}</dd></div>
          </dl>
        </section>
      </div>
    </Dialog>}
  </>;
}

const columns = [
  { key: "number", label: "번호" }, { key: "member", label: "회원" },
  { key: "lessonTitle", label: "수업" }, { key: "time", label: "시간" },
  { key: "statusLabel", label: "출결 상태" },
] as const;
type SortKey = typeof columns[number]["key"];
const collator = new Intl.Collator("ko-KR", { numeric: true });

export function ReservationRoster({ reservations, lessons, memberActionLabel = "회원 상세" }: { reservations: DashboardReservation[]; lessons: DashboardLesson[]; memberActionLabel?: string }) {
  const [sort, setSort] = useState<{ key: SortKey; direction: "asc" | "desc" }>({ key: "number", direction: "asc" });
  const rows = reservations.map((reservation, index) => {
    const lesson = lessons.find(item => item.id === reservation.lessonId)!;
    return { reservation, lesson, number: index + 1, member: reservation.member, lessonTitle: lesson.title, time: Date.parse(lesson.startAt), statusLabel: getStatusLabel({ domain: "reservation", status: reservation.status }) };
  }).sort((a, b) => {
    const left = a[sort.key], right = b[sort.key];
    const comparison = typeof left === "number" && typeof right === "number" ? left - right : collator.compare(String(left), String(right));
    return comparison * (sort.direction === "asc" ? 1 : -1) || a.number - b.number;
  });
  const sortable = (key: SortKey) => key === "member" || (new Set(reservations.map(item => item.lessonId)).size > 1 && (key === "lessonTitle" || key === "time"));
  return <Table variant="management" className="dashboard-lessons-table dashboard-roster-table">
    <TableHeader><TableRow>{columns.map(column => <TableHead key={column.key} scope="col" aria-sort={sortable(column.key) ? sort.key === column.key ? sort.direction === "asc" ? "ascending" : "descending" : "none" : undefined}>
      {sortable(column.key) ? <SortHeaderButton label={column.label} direction={sort.key === column.key ? sort.direction : undefined} onClick={() => setSort(current => ({ key: column.key, direction: current.key === column.key && current.direction === "asc" ? "desc" : "asc" }))} /> : column.label}
    </TableHead>)}<TableHead scope="col">상세</TableHead></TableRow></TableHeader>
    <TableBody>{rows.map(row => <TableRow key={row.reservation.id}>
      <TableCell>{row.number}</TableCell><TableCell><strong>{row.member}</strong></TableCell><TableCell>{row.lessonTitle}</TableCell><TableCell>{seoulTime(row.lesson.startAt)}</TableCell>
      <TableCell><StatusBadge domain="reservation" status={row.reservation.status} /></TableCell><TableCell><MemberInfoButton reservation={row.reservation} lesson={row.lesson} label={memberActionLabel} /></TableCell>
    </TableRow>)}</TableBody>
  </Table>;
}

import { useState } from "react";
import { lessons } from "@/mocks/member";
import type { DialogState } from "@/app/routes";
import { PageTitle } from "@/components/ui/PageTitle";
import { Pagination } from "@/components/ui/Pagination";
import { MenuPanel, MenuEmpty } from "@/components/layout/MenuPanel";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field, FieldLabel } from "@/components/ui/field";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { StatusBadge } from "@/components/ui/StatusBadge";

type Lesson = (typeof lessons)[number];
const initialFilters = { from: "2026-10-12", to: "2026-10-18", type: "all", room: "all" };
const lessonStatus = (lesson: Lesson) => lesson.status === "예약 가능" ? "AVAILABLE" : lesson.status === "대기 신청 가능" ? "WAITING" : "CLOSED";

export function LessonsPage({ onOpen, canReserve, canWaitlist }: { onOpen: (id: number) => void; canReserve: boolean; canWaitlist: boolean }) {
  const [draft, setDraft] = useState(initialFilters);
  const [filters, setFilters] = useState(initialFilters);
  const rows = lessons.filter(lesson => {
    const date = lesson.startAt.slice(0, 10);
    return (!filters.from || date >= filters.from) && (!filters.to || date <= filters.to) && (filters.type === "all" || lesson.type === filters.type) && (filters.room === "all" || lesson.room === filters.room);
  });
  return <div className="menu-page">
    <PageTitle title="수업 목록" description="공개된 수업을 확인하고 나에게 맞는 수업을 예약하세요." />
    <MenuPanel variant="filter"><form className="menu-filters" onSubmit={event => { event.preventDefault(); setFilters(draft); }}>
      <Field><FieldLabel htmlFor="lesson-from">시작일</FieldLabel><Input id="lesson-from" type="date" value={draft.from} max={draft.to || undefined} onChange={event => setDraft({ ...draft, from: event.target.value })} /></Field>
      <Field><FieldLabel htmlFor="lesson-to">종료일</FieldLabel><Input id="lesson-to" type="date" value={draft.to} min={draft.from || undefined} onChange={event => setDraft({ ...draft, to: event.target.value })} /></Field>
      <Field><FieldLabel htmlFor="lesson-type">수업 유형</FieldLabel><Select value={draft.type} onValueChange={type => setDraft({ ...draft, type })}><SelectTrigger id="lesson-type"><SelectValue /></SelectTrigger><SelectContent><SelectItem value="all">전체 유형</SelectItem>{[...new Set(lessons.map(item => item.type))].map(type => <SelectItem key={type} value={type}>{type}</SelectItem>)}</SelectContent></Select></Field>
      <Field><FieldLabel htmlFor="lesson-room">룸</FieldLabel><Select value={draft.room} onValueChange={room => setDraft({ ...draft, room })}><SelectTrigger id="lesson-room"><SelectValue /></SelectTrigger><SelectContent><SelectItem value="all">전체 룸</SelectItem>{[...new Set(lessons.map(item => item.room))].map(room => <SelectItem key={room} value={room}>{room}</SelectItem>)}</SelectContent></Select></Field>
      <div className="menu-actions"><Button variant="outline" type="submit">조회</Button><Button variant="outline" type="button" onClick={() => { setDraft(initialFilters); setFilters(initialFilters); }}>초기화</Button></div>
    </form></MenuPanel>
    <div className="menu-results"><strong>수업 {rows.length}개</strong></div>
    <div className="menu-list">{rows.length ? rows.map(lesson => <LessonCard key={lesson.id} lesson={lesson} onOpen={onOpen} canReserve={canReserve} canWaitlist={canWaitlist} />) : <MenuPanel><MenuEmpty title="조건에 맞는 수업이 없습니다" description="조회 조건을 변경하거나 초기화해 주세요." /></MenuPanel>}</div>
    <Pagination total={rows.length} />
  </div>;
}

function LessonCard({ lesson, onOpen, canReserve, canWaitlist }: { lesson: Lesson; onOpen: (id: number) => void; canReserve: boolean; canWaitlist: boolean }) {
  const status = lessonStatus(lesson);
  const canAct = status === "AVAILABLE" ? canReserve : status === "WAITING" && canWaitlist;
  return <MenuPanel variant={status === "CLOSED" ? "closed" : "lesson"} className="lesson-list-card">
    <div className="menu-lesson-row">
      <div className="menu-lesson-date"><strong>{lesson.date}</strong><span>{lesson.time}</span></div>
      <div className="menu-lesson-info"><h2>{lesson.type}</h2><p>{lesson.teacher} · {lesson.room}</p></div>
      <div className="menu-lesson-meta"><StatusBadge domain="lesson" status={status} /><span>예약 {lesson.booked}</span></div>
      <div className="menu-actions lesson-card-actions"><Button variant="outline" onClick={() => onOpen(lesson.id)}>상세 보기</Button><Button className="lesson-primary-action" variant={status === "WAITING" ? "waiting" : status === "CLOSED" ? "outline" : "default"} disabled={!canAct} onClick={() => onOpen(lesson.id)}>{status === "CLOSED" ? "수업 마감" : status === "WAITING" ? "대기 신청하기" : "수업 예약하기"}</Button></div>
    </div>
  </MenuPanel>;
}

export function LessonDetailPage({ lessonId, onBack, onDialog, canReserve, canWaitlist }: { lessonId: number; onBack: () => void; onDialog: (dialog: DialogState) => void; canReserve: boolean; canWaitlist: boolean }) {
  const lesson = lessons.find(item => item.id === lessonId) ?? lessons[0];
  const status = lessonStatus(lesson);
  const canAct = status === "AVAILABLE" ? canReserve : status === "WAITING" && canWaitlist;
  const waiting = status === "WAITING";
  return <div className="menu-page">
    <PageTitle title="수업 상세" description={`${lesson.date} · ${lesson.type}`} action={<Button variant="outline" onClick={onBack}>목록으로</Button>} />
    <div className="menu-detail-columns">
      <MenuPanel title={lesson.type} action={<StatusBadge domain="lesson" status={status} />}>
        <dl className="menu-facts">{[["수업 유형", lesson.type], ["담당 강사", lesson.teacher], ["룸", lesson.room], ["수업 일시", `2026.${lesson.date} ${lesson.time}`], ["예약 / 정원", lesson.booked]].map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl>
      </MenuPanel>
      <MenuPanel title="내 신청 상태">
        <div className="menu-stack"><p>{status === "CLOSED" ? "예약이 마감된 수업입니다." : waiting ? "정원이 찬 수업입니다. 대기를 신청할 수 있습니다." : "아직 예약하지 않은 수업입니다."}</p><p className="menu-note">예약 시 사용 가능한 이용권에서 1회가 차감됩니다.</p><Button variant={waiting ? "waiting" : status === "CLOSED" ? "outline" : "default"} disabled={!canAct} onClick={() => onDialog({ title: waiting ? "대기 신청 확인" : "수업 예약 확인", body: `${lesson.type} · ${lesson.date} ${lesson.time}`, action: waiting ? "대기 신청하기" : "수업 예약하기" })}>{status === "CLOSED" ? "수업 마감" : waiting ? "대기 신청하기" : "수업 예약하기"}</Button></div>
      </MenuPanel>
    </div>
  </div>;
}

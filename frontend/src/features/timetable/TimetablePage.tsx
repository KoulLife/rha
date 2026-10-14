import { ChevronLeft, ChevronRight } from 'lucide-react';
import { LessonEditor } from '@/features/admin/LessonPages';
import type { Values } from '@/features/admin/AdminUI';
import { useEffect, useState, type CSSProperties } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { useCreateReservation, useGetLessons, useGetMyReservations, getGetMyReservationsQueryKey, type Lesson, type LessonPageResponse } from '@/api/generated/api';
import type { Screen } from '@/config/access';
import type { DashboardUser } from '@/features/dashboard/model';
import { MenuEmpty, MenuPanel } from '@/components/layout/MenuPanel';
import { Dialog } from '@/components/ui/Dialog';
import type { DialogState } from '@/app/routes';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Field, FieldLabel } from '@/components/ui/field';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { StatusBadge, getStatusLabel } from '@/components/ui/StatusBadge';
import { Alert, AlertTitle, AlertDescription } from '@/components/ui/alert';
import { Skeleton } from '@/components/ui/skeleton';
import { ApiError } from '@/api/client';
import { dateInSeoul, layoutDay, shiftDate, timeInSeoul, weekRange, weekStart } from './model';
import './timetable.css';

const weekdays = ['월', '화', '수', '목', '금', '토', '일'];

export function TimetablePage({ user, onNavigate, canReserve = false }: { user: DashboardUser; onNavigate: (screen: Screen, id?: number) => void; canReserve?: boolean }) {
  const today = dateInSeoul();
  const [date, setDate] = useState(today);
  const [room, setRoom] = useState('all');
  const [lessonType, setLessonType] = useState('all');
  const [scope, setScope] = useState('all');
  const [editing, setEditing] = useState(false);
  const [editor, setEditor] = useState<{ id?: number; initialValues?: Values } | null>(null);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const start = weekStart(date);
  const canEdit = user.role === 'ADMIN';
  const openCreate = (day = start < today ? shiftDate(today, 1) : start, hour = 9) => {
    if (!canEdit) return;
    const endDay = hour === 23 ? shiftDate(day, 1) : day;
    setEditor({ initialValues: { startAt: `${day}T${String(hour).padStart(2, '0')}:00`, endAt: `${endDay}T${String((hour + 1) % 24).padStart(2, '0')}:00`, ...(room !== 'all' ? { roomId: room } : {}), ...(lessonType !== 'all' ? { lessonTypeId: lessonType } : {}) } });
  };
  if (editor && canEdit) return <LessonEditor id={editor.id} initialValues={editor.initialValues} onNavigate={onNavigate} onBack={() => setEditor(null)} onSaved={lesson => {
    setDate(dateInSeoul(lesson.startAt)); setRoom('all'); setLessonType('all'); setScope('all'); setSelectedId(lesson.id); setEditor(null);
  }} />;
  return <section className="menu-page timetable-page">
    <h1 className="sr-only">시간표</h1>
    <div className="timetable-toolbar" data-editing={editing}>
      <div className="menu-actions">
        <Field className="timetable-month"><FieldLabel htmlFor="timetable-month" className="sr-only">조회 월</FieldLabel><Input id="timetable-month" type="month" value={date.slice(0, 7)} min="1900-01" max="9998-12" onChange={event => { if (event.target.value && event.target.validity.valid) setDate(`${event.target.value}-01`); }} /></Field>
        <p className="timetable-period" aria-live="polite">{start.slice(5).replace('-', '.')} – {shiftDate(start, 6).slice(5).replace('-', '.')}</p>
      </div>
      <div className="menu-actions">
        <div className="timetable-week-nav" role="group" aria-label="주 이동">
          <Button variant="ghost" aria-label="이전 주" title="이전 주" onClick={() => setDate(shiftDate(start, -7))}><ChevronLeft aria-hidden="true" /></Button>
          <Button variant="ghost" onClick={() => setDate(today)}>이번주</Button>
          <Button variant="ghost" aria-label="다음 주" title="다음 주" onClick={() => setDate(shiftDate(start, 7))}><ChevronRight aria-hidden="true" /></Button>
        </div>
        {canEdit && (editing ? <><Button variant="outline" onClick={() => openCreate()}>수업 추가</Button><Button onClick={() => setEditing(false)}>편집 완료</Button></> : <Button onClick={() => setEditing(true)}>시간표 편집</Button>)}
      </div>
    </div>
    <WeekSchedule key={`${user.role}-${user.id}-${start}`} start={start} today={today} user={user} onNavigate={onNavigate} canReserve={canReserve} room={room} setRoom={setRoom} lessonType={lessonType} setLessonType={setLessonType} scope={scope} setScope={setScope} editing={editing && canEdit} onCreate={openCreate} onEdit={id => { if (canEdit) setEditor({ id }); }} selectedId={selectedId} onSelect={setSelectedId} />
  </section>;
}

type ScheduleProps = { lessonType: string; setLessonType: (value: string) => void; editing: boolean; onCreate: (day: string, hour: number) => void; onEdit: (id: number) => void; selectedId: number | null; onSelect: (id: number) => void; start: string; today: string; user: DashboardUser; onNavigate: (screen: Screen, id?: number) => void; canReserve: boolean; room: string; setRoom: (value: string) => void; scope: string; setScope: (value: string) => void };

function WeekSchedule({ start, today, user, onNavigate, ...filters }: ScheduleProps) {
  const [pages, setPages] = useState<LessonPageResponse[]>([]);
  const [page, setPage] = useState(1);
  // Read the previous day too, so a lesson continuing past midnight appears on Monday.
  const params = { ...weekRange(start), from: weekRange(shiftDate(start, -1)).from, size: 100, page, order: 'ASC' as const };
  const query = useGetLessons(params, { query: { queryKey: ['timetable', user.role, user.id, params] } });
  useEffect(() => {
    if (!query.data) return;
    const response = query.data;
    setPages(previous => [...previous.filter(item => item.pageInfo.page !== response.pageInfo.page), response]);
    if (response.pageInfo.page < response.pageInfo.totalPages) setPage(response.pageInfo.page + 1);
  }, [query.data]);
  const loading = query.isPending || !pages.length || pages.length < (query.data?.pageInfo.totalPages ?? 1);
  const lessons = pages.flatMap(item => item.content).filter(lesson => Date.parse(lesson.endAt) > Date.parse(weekRange(start).from));
  if (query.isError) {
    const status = query.error instanceof ApiError ? query.error.problem.status : undefined;
    return <Alert variant="destructive"><AlertTitle>{status === 401 ? '로그인이 만료되었습니다' : status === 403 ? '조회 권한이 없습니다' : '시간표를 불러오지 못했습니다'}</AlertTitle><AlertDescription>{status === 401 ? '다시 로그인한 뒤 이용해 주세요.' : '잠시 후 다시 시도해 주세요.'}<Button variant="outline" onClick={() => query.refetch()}>다시 시도</Button></AlertDescription></Alert>;
  }
  if (loading) return <div role="status" aria-label="시간표 불러오는 중"><Skeleton className="timetable-loading" /></div>;
  return <Schedule lessons={lessons} start={start} today={today} user={user} onNavigate={onNavigate} {...filters} />;
}

function Schedule({ lessons, start, today, user, onNavigate, canReserve, room, setRoom, lessonType, setLessonType, scope, setScope, editing, onCreate, onEdit, selectedId, onSelect }: ScheduleProps & { lessons: Lesson[] }) {
  const client = useQueryClient();
  const reserve = useCreateReservation();
  const [reservationDialog, setReservationDialog] = useState<DialogState>(null);
  const range = weekRange(start);
  const myReservations = useGetMyReservations({ lessonFrom: range.from, lessonTo: range.to, status: 'RESERVED', page: 1, size: 100 }, { query: { enabled: user.role === 'MEMBER' } });
  const rows = lessons.filter(lesson => (lessonType === 'all' || String(lesson.lessonType.id) === lessonType) && (room === 'all' || String(lesson.room.id) === room) && (scope === 'all' || lesson.instructor.id === user.id));
  const selected = rows.find(lesson => lesson.id === selectedId);
  const dates = Array.from({ length: 7 }, (_, i) => shiftDate(start, i));
  const days = dates.map(date => layoutDay(rows, date));
  const events = days.flat();
  const fromHour = Math.min(9, ...events.map(event => Math.floor(event.start / 60)));
  const toHour = Math.max(21, ...events.map(event => Math.ceil(event.end / 60)));
  const hours = Array.from({ length: toHour - fromHour }, (_, i) => fromHour + i);
  const lessonTypes = [...new Map(lessons.map(lesson => [lesson.lessonType.id, lesson.lessonType])).values()];
  const rooms = [...new Map(lessons.map(lesson => [lesson.room.id, lesson.room])).values()];
  return <>
    <div className="timetable-toolbar timetable-filters">
      <div className="menu-actions">
        {user.role === 'INSTRUCTOR' && <Field><FieldLabel htmlFor="timetable-scope" className="sr-only">수업 범위</FieldLabel><Select value={scope} onValueChange={setScope}><SelectTrigger id="timetable-scope"><SelectValue /></SelectTrigger><SelectContent><SelectItem value="all">조회 가능한 전체 수업</SelectItem><SelectItem value="own">내 담당 수업</SelectItem></SelectContent></Select></Field>}
        <Field><FieldLabel htmlFor="timetable-type" className="sr-only">수업 유형</FieldLabel><Select value={lessonType} onValueChange={setLessonType}><SelectTrigger id="timetable-type"><SelectValue /></SelectTrigger><SelectContent><SelectItem value="all">전체 수업 유형</SelectItem>{lessonType !== 'all' && !lessonTypes.some(item => String(item.id) === lessonType) && <SelectItem value={lessonType}>선택한 유형 (수업 없음)</SelectItem>}{lessonTypes.map(item => <SelectItem key={item.id} value={String(item.id)}>{item.name}</SelectItem>)}</SelectContent></Select></Field>
        <Field><FieldLabel htmlFor="timetable-room" className="sr-only">룸</FieldLabel><Select value={room} onValueChange={setRoom}><SelectTrigger id="timetable-room"><SelectValue /></SelectTrigger><SelectContent><SelectItem value="all">전체 룸</SelectItem>{room !== 'all' && !rooms.some(item => String(item.id) === room) && <SelectItem value={room}>선택한 룸 (수업 없음)</SelectItem>}{rooms.map(item => <SelectItem key={item.id} value={String(item.id)}>{item.name}</SelectItem>)}</SelectContent></Select></Field>
      </div>
    </div>
    {!rows.length && !editing ? <MenuPanel><MenuEmpty title="이번 주에 표시할 수업이 없습니다" description="다른 주를 선택하거나 수업 유형·범위·룸을 변경해 주세요." /></MenuPanel> : <div className="timetable-layout" data-editing={editing}>
      <div className="timetable-scroll" role="region" aria-label="주간 수업 시간표" tabIndex={0}>
        <div className="timetable-grid" style={{ '--timetable-hours': hours.length } as CSSProperties}>
          <div className="timetable-header timetable-corner">시간</div>
          {dates.map((date, i) => <div key={date} className="timetable-header" data-today={date === today}><span>{weekdays[i]}</span><strong>{date.slice(5).replace('-', '.')}</strong>{date === today && <span className="timetable-today">오늘</span>}</div>)}
          <div className="timetable-times" aria-hidden="true">{hours.map(hour => <div key={hour}>{String(hour).padStart(2, '0')}:00</div>)}</div>
          {days.map((day, i) => <div key={dates[i]} className="timetable-day" data-today={dates[i] === today} aria-label={`${dates[i]} ${weekdays[i]}요일`}>
            {hours.map(hour => editing ? <button key={hour} type="button" className="timetable-hour timetable-slot" aria-label={`${dates[i]} ${String(hour).padStart(2, '0')}:00 수업 등록`} disabled={Date.parse(`${dates[i]}T${String(hour).padStart(2, '0')}:00+09:00`) <= Date.now()} onClick={() => onCreate(dates[i], hour)}><span aria-hidden="true">수업 추가</span></button> : <div key={hour} className="timetable-hour" aria-hidden="true" />)}
            {day.map(({ lesson, start: eventStart, end, column, columns }) => <button type="button" key={lesson.id} className="timetable-event" data-status={lesson.status} aria-pressed={selectedId === lesson.id}
              aria-label={`${dates[i]} ${timeInSeoul(lesson.startAt)} ${lesson.title} · ${lesson.instructor.name} · ${lesson.room.name} · 예약 ${lesson.reservedCount}/${lesson.capacity} · ${getStatusLabel({ domain: 'lesson', status: lesson.status })}`}
              title={`${timeInSeoul(lesson.startAt)}–${timeInSeoul(lesson.endAt)} ${lesson.title} · ${lesson.instructor.name} · ${lesson.room.name}`}
              style={{ top: `${(eventStart - fromHour * 60) / (hours.length * 60) * 100}%`, height: `${(end - eventStart) / (hours.length * 60) * 100}%`, left: `${column / columns * 100}%`, width: `${100 / columns}%` }} onClick={() => onSelect(lesson.id)}>
              <span className="timetable-event-meta"><span className="timetable-event-time">{timeInSeoul(lesson.startAt)}</span><span>{lesson.reservedCount}/{lesson.capacity}</span></span><strong>{lesson.title}{lesson.status !== 'PUBLISHED' && <span className="timetable-event-status"> · {getStatusLabel({ domain: 'lesson', status: lesson.status })}</span>}</strong>
            </button>)}
          </div>)}
        </div>
      </div>
      <aside className="timetable-preview" aria-label="선택한 수업 요약" aria-live="polite">
        <MenuPanel title="선택한 수업">{selected ? <div className="menu-stack">
          <div className="menu-stack"><h3>{selected.title}</h3><StatusBadge domain="lesson" status={selected.status} /></div>
          <dl className="menu-facts">{[['날짜', dateInSeoul(selected.startAt)], ['시간', `${timeInSeoul(selected.startAt)} – ${dateInSeoul(selected.endAt) !== dateInSeoul(selected.startAt) ? `${dateInSeoul(selected.endAt)} ` : ''}${timeInSeoul(selected.endAt)}`], ['수업 유형', selected.lessonType.name], ['담당 강사', selected.instructor.name], ['룸', selected.room.name], ['예약 / 정원', `${selected.reservedCount} / ${selected.capacity}명`]].map(([label, value]) => <div key={label}><dt>{label}</dt><dd>{value}</dd></div>)}</dl>
          {selected.status === 'PUBLISHED' && <p className="menu-note">{Date.parse(selected.endAt) <= Date.now() ? '종료된 수업입니다.' : selected.reservedCount >= selected.capacity ? '정원이 찬 수업입니다.' : `남은 자리 ${selected.capacity - selected.reservedCount}석`}</p>}
          {user.role === 'MEMBER' && selected.status === 'PUBLISHED' && Date.parse(selected.startAt) > Date.now() && (() => {
            const alreadyReserved = myReservations.data?.content.some(reservation => reservation.lesson.id === selected.id && reservation.status === 'RESERVED');
            if (alreadyReserved) return <p className="menu-note">이미 신청한 수업입니다.</p>;
            if (selected.reservedCount >= selected.capacity) return null;
            if (!canReserve) return <p className="menu-note">수업 신청 권한이 없습니다.</p>;
            return <Button disabled={myReservations.isPending || reserve.isPending} onClick={() => setReservationDialog({ title: '수업 신청 확인', body: `${selected.title} · ${dateInSeoul(selected.startAt)} ${timeInSeoul(selected.startAt)}`, action: '신청하기', details: [['수업', selected.title], ['일시', `${dateInSeoul(selected.startAt)} ${timeInSeoul(selected.startAt)}`], ['담당 강사', selected.instructor.name], ['룸', selected.room.name]].map(([label, value]) => ({ label, value })), notice: '신청이 완료되면 사용 가능한 이용권에서 1회가 차감됩니다.' })}>수업 신청하기</Button>;
          })()}
          {editing && selected.status !== 'CANCELED' && Date.parse(selected.startAt) > Date.now() && <Button onClick={() => onEdit(selected.id)}>수업 수정</Button>}
          {user.role === 'ADMIN' && <Button variant="outline" onClick={() => onNavigate('admin-lesson-detail', selected.id)}>수업 상세 보기</Button>}
        </div> : <p className="timetable-empty-preview">{editing ? '빈 시간칸을 선택해 수업을 등록하세요.' : '수업을 선택해 주세요.'}</p>}</MenuPanel>
      </aside>
    </div>}
    {reservationDialog && <Dialog dialog={reservationDialog} onClose={() => setReservationDialog(null)} onConfirm={() => {
      const selectedLesson = selected;
      if (!selectedLesson) return;
      void reserve.mutateAsync({ data: { lessonId: selectedLesson.id } }).then(async () => {
        await Promise.all([
          client.invalidateQueries({ queryKey: ['timetable', user.role, user.id] }),
          client.invalidateQueries({ queryKey: getGetMyReservationsQueryKey() }),
        ]);
        toast.success('수업 신청이 완료되었습니다.');
      }).catch(error => toast.error(error instanceof Error ? error.message : '수업 신청에 실패했습니다.'));
    }} />}
  </>;
}

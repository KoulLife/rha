import { useState } from "react";
import { lessons } from "@/mocks/member";
import type { DialogState } from "@/app/routes";
import { PageTitle } from "@/components/ui/PageTitle";
import { Pagination, PAGE_SIZE } from "@/components/ui/Pagination";
import { MenuPanel, MenuEmpty } from "@/components/layout/MenuPanel";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Field, FieldLabel } from "@/components/ui/field";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import "./lessons.css";
import { StatusBadge } from "@/components/ui/StatusBadge";

type Lesson = (typeof lessons)[number];
const initialFilters = { from: "2026-10-12", to: "2026-10-18", type: "all", room: "all" };
const lessonStatus = (lesson: Lesson) =>
  lesson.status === "예약 가능"
    ? "AVAILABLE"
    : lesson.status === "대기 신청 가능"
      ? "WAITING"
      : "CLOSED";
const lessonConfirmation = (lesson: Lesson, waiting: boolean): NonNullable<DialogState> => ({
  title: waiting ? "대기 신청 확인" : "수업 예약 확인",
  body: `${lesson.type}, ${lesson.date} ${lesson.time}, ${lesson.teacher}, ${lesson.room}`,
  details: [
    { label: "수업", value: lesson.type },
    { label: "일시", value: `2026.${lesson.date} ${lesson.time}` },
    { label: "담당 강사", value: lesson.teacher },
    { label: "룸", value: lesson.room },
  ],
  notice: waiting
    ? "대기 순번이 되어 예약이 확정되면 이용권 1회가 차감됩니다."
    : "예약이 확정되면 이용권 1회가 차감됩니다. 예약 취소 시 횟수는 복구됩니다.",
  actionVariant: waiting ? "warning" : "default",
  action: waiting ? "대기 신청하기" : "수업 예약하기",
});

export function LessonsPage({
  onOpen,
  onDialog,
  canReserve,
  canWaitlist,
}: {
  onOpen: (id: number) => void;
  onDialog: (dialog: NonNullable<DialogState>) => void;
  canReserve: boolean;
  canWaitlist: boolean;
}) {
  const [draft, setDraft] = useState(initialFilters);
  const filters = draft;
  const [page, setPage] = useState(1);
  const rows = lessons.filter((lesson) => {
    const date = lesson.startAt.slice(0, 10);
    return (
      (!filters.from || date >= filters.from) &&
      (!filters.to || date <= filters.to) &&
      (filters.type === "all" || lesson.type === filters.type) &&
      (filters.room === "all" || lesson.room === filters.room)
    );
  });
  const totalPages = Math.max(1, Math.ceil(rows.length / PAGE_SIZE));
  const currentPage = Math.min(page, totalPages);
  return (
    <div className="menu-page">
      <PageTitle
        title="수업 목록"
        description="공개된 수업을 확인하고 나에게 맞는 수업을 예약하세요."
      />
      <MenuPanel variant="filter">
        <div className="menu-filters">
          <Field>
            <FieldLabel htmlFor="lesson-from">시작일</FieldLabel>
            <Input
              id="lesson-from"
              type="date"
              value={draft.from}
              max={draft.to || undefined}
              onChange={(event) => {
                setDraft({ ...draft, from: event.target.value });
                setPage(1);
              }}
            />
          </Field>
          <Field>
            <FieldLabel htmlFor="lesson-to">종료일</FieldLabel>
            <Input
              id="lesson-to"
              type="date"
              value={draft.to}
              min={draft.from || undefined}
              onChange={(event) => {
                setDraft({ ...draft, to: event.target.value });
                setPage(1);
              }}
            />
          </Field>
          <Field>
            <FieldLabel htmlFor="lesson-type">수업 유형</FieldLabel>
            <Select
              value={draft.type}
              onValueChange={(type) => {
                setDraft({ ...draft, type });
                setPage(1);
              }}
            >
              <SelectTrigger id="lesson-type">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">전체 유형</SelectItem>
                {[...new Set(lessons.map((item) => item.type))].map((type) => (
                  <SelectItem
                    key={type}
                    value={type}
                  >
                    {type}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </Field>
          <Field>
            <FieldLabel htmlFor="lesson-room">룸</FieldLabel>
            <Select
              value={draft.room}
              onValueChange={(room) => {
                setDraft({ ...draft, room });
                setPage(1);
              }}
            >
              <SelectTrigger id="lesson-room">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="all">전체 룸</SelectItem>
                {[...new Set(lessons.map((item) => item.room))].map((room) => (
                  <SelectItem
                    key={room}
                    value={room}
                  >
                    {room}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </Field>
        </div>
      </MenuPanel>
      <MenuPanel variant="table">
        {rows.length ? (
          <Table
            variant="management"
            className="member-lessons-table"
            aria-label="수업 목록"
          >
            <TableHeader>
              <TableRow>
                {[
                  "날짜",
                  "시간",
                  "수업명",
                  "담당 강사",
                  "룸",
                  "예약 / 정원",
                  "예약 상태",
                  "상세",
                  "신청",
                ].map((label) => (
                  <TableHead
                    key={label}
                    scope="col"
                  >
                    {label}
                  </TableHead>
                ))}
              </TableRow>
            </TableHeader>
            <TableBody>
              {rows.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE).map((lesson) => (
                <LessonRow
                  key={lesson.id}
                  lesson={lesson}
                  onOpen={onOpen}
                  onDialog={onDialog}
                  canReserve={canReserve}
                  canWaitlist={canWaitlist}
                />
              ))}
            </TableBody>
          </Table>
        ) : (
          <MenuEmpty
            title="조건에 맞는 수업이 없습니다"
            description="조회 조건을 변경해 주세요."
          />
        )}
      </MenuPanel>
      <Pagination
        total={rows.length}
        page={currentPage}
        totalPages={totalPages}
        onPageChange={setPage}
      />
    </div>
  );
}

function LessonRow({
  lesson,
  onOpen,
  onDialog,
  canReserve,
  canWaitlist,
}: {
  lesson: Lesson;
  onOpen: (id: number) => void;
  onDialog: (dialog: NonNullable<DialogState>) => void;
  canReserve: boolean;
  canWaitlist: boolean;
}) {
  const status = lessonStatus(lesson);
  const canAct = status === "AVAILABLE" ? canReserve : status === "WAITING" && canWaitlist;
  const waiting = status === "WAITING";
  return (
    <TableRow>
      <TableCell>{lesson.date}</TableCell>
      <TableCell>{lesson.time}</TableCell>
      <TableCell>
        <strong>{lesson.type}</strong>
      </TableCell>
      <TableCell>{lesson.teacher}</TableCell>
      <TableCell>{lesson.room}</TableCell>
      <TableCell>{lesson.booked}</TableCell>
      <TableCell>
        <StatusBadge
          domain="lesson"
          status={status}
        />
      </TableCell>
      <TableCell>
        <Button
          variant="outline"
          size="sm"
          onClick={() => onOpen(lesson.id)}
          aria-label={`${lesson.type} 상세 보기`}
        >
          상세 보기
        </Button>
      </TableCell>
      <TableCell>
        <Button
          variant={waiting ? "warning" : status === "CLOSED" ? "outline" : "default"}
          disabled={!canAct}
          onClick={() => onDialog(lessonConfirmation(lesson, waiting))}
        >
          {status === "CLOSED" ? "수업 마감" : waiting ? "대기 신청하기" : "수업 예약하기"}
        </Button>
      </TableCell>
    </TableRow>
  );
}

export function LessonDetailPage({
  lessonId,
  onBack,
  onDialog,
  canReserve,
  canWaitlist,
}: {
  lessonId: number;
  onBack: () => void;
  onDialog: (dialog: DialogState) => void;
  canReserve: boolean;
  canWaitlist: boolean;
}) {
  const lesson = lessons.find((item) => item.id === lessonId) ?? lessons[0];
  const status = lessonStatus(lesson);
  const canAct = status === "AVAILABLE" ? canReserve : status === "WAITING" && canWaitlist;
  const waiting = status === "WAITING";
  return (
    <div className="menu-page">
      <PageTitle
        title="수업 상세"
        description={`${lesson.date} · ${lesson.type}`}
        action={
          <Button
            variant="outline"
            onClick={onBack}
          >
            목록으로
          </Button>
        }
      />
      <div className="menu-detail-columns">
        <MenuPanel
          title={lesson.type}
          action={
            <StatusBadge
              domain="lesson"
              status={status}
            />
          }
        >
          <dl className="menu-facts">
            {[
              ["수업 유형", lesson.type],
              ["담당 강사", lesson.teacher],
              ["룸", lesson.room],
              ["수업 일시", `2026.${lesson.date} ${lesson.time}`],
              ["예약 / 정원", lesson.booked],
            ].map(([label, value]) => (
              <div key={label}>
                <dt>{label}</dt>
                <dd>{value}</dd>
              </div>
            ))}
          </dl>
        </MenuPanel>
        <MenuPanel title="내 신청 상태">
          <div className="menu-stack">
            <p>
              {status === "CLOSED"
                ? "예약이 마감된 수업입니다."
                : waiting
                  ? "정원이 찬 수업입니다. 대기를 신청할 수 있습니다."
                  : "아직 예약하지 않은 수업입니다."}
            </p>
            <p className="menu-note">예약 시 사용 가능한 이용권에서 1회가 차감됩니다.</p>
            <Button
              variant={waiting ? "warning" : status === "CLOSED" ? "outline" : "default"}
              disabled={!canAct}
              onClick={() => onDialog(lessonConfirmation(lesson, waiting))}
            >
              {status === "CLOSED" ? "수업 마감" : waiting ? "대기 신청하기" : "수업 예약하기"}
            </Button>
          </div>
        </MenuPanel>
      </div>
    </div>
  );
}

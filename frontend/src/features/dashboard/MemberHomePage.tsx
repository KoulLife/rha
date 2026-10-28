import { useState, type ReactNode } from "react";
import { cva } from "class-variance-authority";
import type { Screen } from "@/app/routes";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Empty, EmptyDescription, EmptyHeader, EmptyTitle } from "@/components/ui/empty";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { useGetLessons, useGetMyPasses, useGetMyReservations } from "@/api/generated/api";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { currentTime } from "@/api/runtime";
import { seoulDate, seoulTime } from "./model";
import { HOME_LESSON_LIMIT, homeLessonStatus, upcomingLessons } from "./homeLessons";
import { dateInSeoul, timeInSeoul } from "@/features/timetable/model";
import { PassUsage } from "@/features/member/PassUsage";
import { passUsage } from "@/features/member/pages/PassesPage";
import { nextPass } from "@/features/member/nextPass";
import "./member-home.css";

const weekdayFormatter = new Intl.DateTimeFormat("ko-KR", {
  timeZone: "Asia/Seoul",
  weekday: "short",
});

const panelVariants = cva("home-panel", {
  variants: {
    variant: {
      public: "home-panel-public",
      reservations: "home-panel-reservations",
      pass: "home-panel-pass",
    },
  },
});

function HomePanel({
  title,
  action,
  children,
  variant,
}: {
  title: string;
  action?: ReactNode;
  children: ReactNode;
  variant: "public" | "reservations" | "pass";
}) {
  return (
    <Card
      size="sm"
      className={panelVariants({ variant })}
    >
      <CardHeader className="home-panel-heading">
        <CardTitle>
          <h2>{title}</h2>
        </CardTitle>
        {action}
      </CardHeader>
      <CardContent>{children}</CardContent>
    </Card>
  );
}

export function MemberHomePage({
  onNavigate,
  empty = false,
}: {
  onNavigate: (screen: Screen, id?: number) => void;
  empty?: boolean;
}) {
  const passQuery = useGetMyPasses({ page: 1, size: 100 }, { query: { enabled: !empty } });
  const reservedQuery = useGetMyReservations(
    { page: 1, size: 3, status: "RESERVED", order: "ASC" },
    { query: { enabled: !empty } },
  );
  const allReservationsQuery = useGetMyReservations(
    { page: 1, size: 1 },
    { query: { enabled: !empty } },
  );
  const pass = nextPass(passQuery.data?.content ?? []);
  const reserved = reservedQuery.data?.content ?? [];
  // 화면을 연 시각 이후에 시작하는 공개 수업. 조회 키가 매번 바뀌지 않도록 연 시각을 고정한다.
  const [openedAt] = useState(currentTime);
  const lessonsQuery = useGetLessons(
    { from: openedAt, status: "PUBLISHED", order: "ASC", page: 1, size: HOME_LESSON_LIMIT },
    { query: { enabled: !empty } },
  );
  const now = Date.parse(currentTime());
  const publicLessons = empty ? [] : upcomingLessons(lessonsQuery.data?.content ?? [], now);
  const metrics: { label: string; value: string; target: Screen }[] = [
    {
      label: "예약 완료",
      value: reservedQuery.data ? String(reservedQuery.data.pageInfo.totalElements) : "—",
      target: "reservations",
    },
    {
      label: "보유 이용권",
      value: passQuery.data ? String(passQuery.data.pageInfo.totalElements) : "—",
      target: "passes",
    },
    {
      label: "이용권 현재 잔여",
      value: passQuery.data ? `${pass?.remainingCount ?? 0}회` : "—",
      target: "passes",
    },
    {
      label: "전체 예약 내역",
      value: allReservationsQuery.data
        ? String(allReservationsQuery.data.pageInfo.totalElements)
        : "—",
      target: "reservations",
    },
  ];

  return (
    <div className="home-page">
      <div className="home-heading">
        <div>
          <h1>내 수업과 신청 현황</h1>
        </div>
        <div className="home-heading-actions">
          <Button
            variant="outline"
            onClick={() => onNavigate("classes")}
          >
            수업 목록 보기
          </Button>
        </div>
      </div>
      <section
        className="home-metrics"
        aria-label="내 현황 요약"
      >
        {metrics.map((item) => (
          <Card
            size="sm"
            className="home-metric"
            key={item.label}
          >
            <CardContent>
              <Button
                variant="ghost"
                className="home-metric-link"
                onClick={() => onNavigate(item.target)}
                aria-label={`${item.label} 내역 확인`}
              >
                <span className="home-metric-label">{item.label}</span>
                <strong>{item.value}</strong>
              </Button>
            </CardContent>
          </Card>
        ))}
      </section>
      <div className="home-columns">
        <HomePanel
          variant="public"
          title="수업 목록"
          action={
            <Button
              variant="ghost"
              onClick={() => onNavigate("timetable")}
            >
              전체 보기
            </Button>
          }
        >
          {publicLessons.length ? (
            <Table
              variant="management"
              className="home-public-table"
              aria-label="예정된 수업 목록"
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
                {publicLessons.map((lesson) => (
                  <TableRow key={lesson.id}>
                    <TableCell>
                      {dateInSeoul(lesson.startAt).slice(5).replace("-", ".")} (
                      {weekdayFormatter.format(new Date(lesson.startAt))})
                    </TableCell>
                    <TableCell>
                      {timeInSeoul(lesson.startAt)}–{timeInSeoul(lesson.endAt)}
                    </TableCell>
                    <TableCell>
                      <strong>{lesson.title}</strong>
                    </TableCell>
                    <TableCell>{lesson.instructor.name}</TableCell>
                    <TableCell>{lesson.room.name}</TableCell>
                    <TableCell>
                      {lesson.reservedCount} / {lesson.capacity}명
                    </TableCell>
                    <TableCell>
                      <StatusBadge
                        domain="lesson"
                        status={homeLessonStatus(lesson, now)}
                      />
                    </TableCell>
                    <TableCell>
                      <Button
                        variant="outline"
                        size="sm"
                        onClick={() => onNavigate("lesson-detail", lesson.id)}
                        aria-label={`${lesson.title} 상세 보기`}
                      >
                        상세 보기
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          ) : (
            <Empty>
              <EmptyHeader>
                <EmptyTitle>
                  {lessonsQuery.isPending && !empty
                    ? "수업을 불러오는 중입니다"
                    : lessonsQuery.isError
                      ? "수업을 불러오지 못했습니다"
                      : "예정된 수업이 없습니다"}
                </EmptyTitle>
                <EmptyDescription>
                  {lessonsQuery.isError
                    ? "잠시 후 다시 확인해 주세요."
                    : "새 수업이 공개되면 이곳에서 확인할 수 있습니다."}
                </EmptyDescription>
              </EmptyHeader>
            </Empty>
          )}
        </HomePanel>
        <div className="home-side">
          <HomePanel
            variant="reservations"
            title="내가 예약한 수업"
          >
            {reserved.length ? (
              <div className="home-reservation-list">
                {reserved.map((item) => {
                  const lesson = item.lesson;
                  return (
                    <div
                      className="home-reservation"
                      key={item.id}
                    >
                      <div>
                        <h3>{lesson.title}</h3>
                        {lesson && (
                          <p>
                            {seoulDate(lesson.startAt).replace(/-/g, ".")}{" "}
                            {seoulTime(lesson.startAt)} · {lesson.instructor?.name ?? "-"}
                          </p>
                        )}
                      </div>
                      <div className="home-reservation-action">
                        <StatusBadge
                          domain="reservation"
                          status="RESERVED"
                        />
                        <Button
                          variant="ghost"
                          onClick={() => onNavigate("reservations")}
                        >
                          내역 보기
                        </Button>
                      </div>
                    </div>
                  );
                })}
              </div>
            ) : (
              <Empty>
                <EmptyHeader>
                  <EmptyTitle>
                    {reservedQuery.isPending && !empty
                      ? "예약을 불러오는 중입니다"
                      : reservedQuery.isError
                        ? "예약을 불러오지 못했습니다"
                        : "예약한 수업이 없습니다"}
                  </EmptyTitle>
                  <EmptyDescription>
                    {reservedQuery.isError
                      ? "잠시 후 다시 확인해 주세요."
                      : "수업 목록에서 예약할 수 있습니다."}
                  </EmptyDescription>
                </EmptyHeader>
              </Empty>
            )}
          </HomePanel>
          <HomePanel
            variant="pass"
            title="사용 중인 이용권"
            action={
              <Button
                variant="ghost"
                onClick={() => (pass ? onNavigate("pass-detail", pass.id) : onNavigate("passes"))}
              >
                상세 보기
              </Button>
            }
          >
            {pass ? (
              <>
                <div className="home-pass-heading">
                  <h3>{pass.passProduct.name}</h3>
                  <p className="home-pass-note">예약하면 이 이용권에서 1회씩 차감됩니다.</p>
                </div>
                <PassUsage
                  pass={passUsage(pass)}
                  compact
                />
                <div className="home-pass-footer">
                  <StatusBadge
                    domain="pass"
                    status={pass.status}
                  />
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => onNavigate("timetable", pass.instructor.id)}
                  >
                    담당 강사 수업 보기
                  </Button>
                </div>
              </>
            ) : (
              <Empty>
                <EmptyHeader>
                  <EmptyTitle>
                    {passQuery.isPending && !empty
                      ? "이용권을 불러오는 중입니다"
                      : passQuery.isError
                        ? "이용권을 불러오지 못했습니다"
                        : "사용 중인 이용권이 없습니다"}
                  </EmptyTitle>
                  <EmptyDescription>
                    {passQuery.isError
                      ? "잠시 후 다시 확인해 주세요."
                      : "내 이용권에서 잔여 횟수와 만료일을 확인할 수 있습니다."}
                  </EmptyDescription>
                </EmptyHeader>
              </Empty>
            )}
          </HomePanel>
        </div>
      </div>
    </div>
  );
}

import { useEffect, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import * as api from "@/api/generated/api";
import { currentTime } from "@/api/runtime";
import { MenuEmpty, MenuPanel } from "@/components/layout/MenuPanel";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/Dialog";
import { Pagination, PAGE_SIZE } from "@/components/ui/Pagination";
import { LessonReservationSummary } from "@/components/ui/ReservationSummary";
import { StatusBadge } from "@/components/ui/StatusBadge";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { ActionDialog, Facts, QueryState, textValue, type Action } from "@/features/admin/AdminUI";
import { seoulDate, seoulTime, type DashboardUser } from "@/features/dashboard/model";
import type { AttendanceChange } from "./model";
import "./instructor.css";

type Props = {
  lessonId: number | null;
  user: DashboardUser;
  canViewReservations: boolean;
  changes: AttendanceChange[];
  onRecorded: (
    reservation: api.Reservation,
    status: api.AttendanceRequestStatus,
    reason: string,
  ) => void;
};

export function InstructorLessonRoster({
  lessonId,
  user,
  canViewReservations,
  changes,
  onRecorded,
}: Props) {
  const client = useQueryClient();
  const [page, setPage] = useState(1);
  const [detail, setDetail] = useState<api.Reservation | null>(null);
  const [action, setAction] = useState<Action | null>(null);
  const lesson = api.useGetLesson(lessonId ?? 0, { query: { enabled: lessonId !== null } });
  const ownLesson = lesson.data?.instructor.id === user.id;
  const roster = api.useGetLessonReservations(
    lessonId ?? 0,
    { page, size: PAGE_SIZE },
    { query: { enabled: lessonId !== null && ownLesson && canViewReservations } },
  );
  const attendance = api.useConfirmAttendance();
  const visibleHistory = changes.filter(
    (item) => item.lessonId === lessonId && item.before !== "RESERVED",
  );
  useEffect(() => setPage(1), [lessonId]);

  if (lessonId === null) return null;

  const openAttendance = (reservation: api.Reservation) => {
    const correction = reservation.status !== "RESERVED";
    setAction({
      title: correction ? "출결 정정" : "출결 처리",
      description: `${reservation.member.name}님의 출결을 기록합니다.${correction ? " 정정 사유는 이번 화면에서만 보관되며 서버에 저장되지 않습니다." : ""}`,
      submitLabel: correction ? "출결 정정" : "출결 저장",
      fields: [
        {
          name: "status",
          label: "출결",
          type: "select",
          required: true,
          options: [
            { value: "ATTENDED", label: "출석" },
            { value: "NO_SHOW", label: "노쇼" },
          ],
        },
        ...(correction
          ? [
              {
                name: "reason",
                label: "정정 사유 (이번 화면에만 보관)",
                type: "textarea" as const,
                required: true,
                maxLength: 500,
              },
            ]
          : []),
      ],
      initial: { status: reservation.status === "ATTENDED" ? "NO_SHOW" : "ATTENDED" },
      onSubmit: async (values) => {
        const status = textValue(values, "status") as api.AttendanceRequestStatus;
        const reason = textValue(values, "reason").trim();
        if (correction && reservation.status === status)
          throw new Error("현재 출결과 다른 상태를 선택해 주세요.");
        if (correction && !reason) throw new Error("정정 사유를 입력해 주세요.");
        await attendance.mutateAsync({ reservationId: reservation.id, data: { status } });
        await client.invalidateQueries({
          queryKey: api.getGetLessonReservationsQueryKey(lessonId ?? 0),
        });
        await client.invalidateQueries({
          queryKey: api.getGetLessonReservationsInfiniteQueryKey(lessonId ?? 0),
        });
        await client.invalidateQueries({ queryKey: api.getGetReservationQueryKey(reservation.id) });
        await client.invalidateQueries({ queryKey: api.getGetReservationsQueryKey() });
        await client.invalidateQueries({ queryKey: api.getGetReservationsInfiniteQueryKey() });
        onRecorded(reservation, status, reason);
        toast.success(correction ? "출결을 정정했습니다." : "출결을 기록했습니다.");
      },
    });
  };

  return (
    <section
      className="instructor-roster"
      aria-label="선택한 수업의 예약자와 출결"
    >
      <QueryState query={lesson}>
        {lesson.data && (
          <>
            {!ownLesson ? (
              <MenuPanel>
                <MenuEmpty
                  title="담당 수업의 예약자만 볼 수 있습니다"
                  description="다른 강사의 수업 정보는 시간표에서 확인할 수 있습니다."
                />
              </MenuPanel>
            ) : !canViewReservations ? (
              <MenuPanel>
                <MenuEmpty
                  title="예약자 조회 권한이 없습니다"
                  description="수업 정보는 확인할 수 있지만 예약자와 출결은 조회 권한이 필요합니다."
                />
              </MenuPanel>
            ) : (
              <QueryState query={roster}>
                <MenuPanel
                  variant="table"
                  className="instructor-roster-panel"
                  title="예약자"
                  action={<LessonReservationSummary lessonId={lessonId} />}
                >
                  {roster.data?.content.length ? (
                    <Table
                      variant="management"
                      aria-label={`${lesson.data.title} 예약자 목록`}
                    >
                      <TableHeader>
                        <TableRow>
                          <TableHead>회원</TableHead>
                          <TableHead>이용권</TableHead>
                          <TableHead>예약·출결 상태</TableHead>
                          <TableHead>회원·예약 정보</TableHead>
                          <TableHead>출결 처리</TableHead>
                        </TableRow>
                      </TableHeader>
                      <TableBody>
                        {roster.data.content.map((reservation) => (
                          <TableRow key={reservation.id}>
                            <TableCell>
                              <strong>{reservation.member.name}</strong>
                            </TableCell>
                            <TableCell>{reservation.pass.passProduct.name}</TableCell>
                            <TableCell>
                              <StatusBadge
                                domain="reservation"
                                status={reservation.status}
                              />
                            </TableCell>
                            <TableCell>
                              <Button
                                variant="outline"
                                size="table-action"
                                onClick={() => setDetail(reservation)}
                              >
                                정보 보기
                              </Button>
                            </TableCell>
                            <TableCell>
                              {reservation.status === "CANCELED" ||
                              lesson.data!.status === "CANCELED" ? (
                                <span className="menu-muted">처리 불가</span>
                              ) : Date.parse(lesson.data!.startAt) > Date.parse(currentTime()) ? (
                                <span className="menu-muted">수업 시작 후 가능</span>
                              ) : (
                                <Button
                                  variant="outline"
                                  size="table-action"
                                  onClick={() => openAttendance(reservation)}
                                >
                                  {reservation.status === "RESERVED" ? "출결 처리" : "출결 정정"}
                                </Button>
                              )}
                            </TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  ) : (
                    <MenuEmpty
                      title="예약자가 없습니다"
                      description="이 수업에 예약된 회원이 없습니다."
                    />
                  )}
                </MenuPanel>
                {(roster.data?.pageInfo.totalElements ?? 0) > PAGE_SIZE && (
                  <Pagination
                    total={roster.data?.pageInfo.totalElements ?? 0}
                    page={roster.data?.pageInfo.page ?? page}
                    pageSize={PAGE_SIZE}
                    onPageChange={setPage}
                  />
                )}
              </QueryState>
            )}
            {ownLesson && canViewReservations && visibleHistory.length > 0 && (
              <MenuPanel
                variant="table"
                className="instructor-roster-panel"
                title="이번 화면의 출결 정정 이력 (새로고침 시 초기화)"
                action={<span>{visibleHistory.length}건</span>}
              >
                <Table
                  variant="management"
                  aria-label="출결 정정 이력"
                >
                  <TableHeader>
                    <TableRow>
                      <TableHead>회원</TableHead>
                      <TableHead>변경 전</TableHead>
                      <TableHead>변경 후</TableHead>
                      <TableHead>정정 사유</TableHead>
                      <TableHead>처리 일시</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {visibleHistory.map((change) => (
                      <TableRow key={change.id}>
                        <TableCell>{change.member ?? `예약 #${change.reservationId}`}</TableCell>
                        <TableCell>
                          <StatusBadge
                            domain="reservation"
                            status={change.before}
                          />
                        </TableCell>
                        <TableCell>
                          <StatusBadge
                            domain="reservation"
                            status={change.after}
                          />
                        </TableCell>
                        <TableCell>
                          <span className="instructor-wrap">{change.reason}</span>
                        </TableCell>
                        <TableCell>
                          {seoulDate(change.at)} {seoulTime(change.at)}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </MenuPanel>
            )}
          </>
        )}
      </QueryState>
      {detail && (
        <Dialog
          dialog={{
            title: "회원·예약 정보",
            body: `${detail.member.name}님의 선택 수업 예약 정보입니다.`,
          }}
          onClose={() => setDetail(null)}
        >
          <Facts
            items={[
              ["회원", detail.member.name],
              ["회원 번호", `#${detail.member.id}`],
              ["예약 번호", `#${detail.id}`],
              ["수업", detail.lesson.title],
              ["이용권", detail.pass.passProduct.name],
              [
                "예약·출결 상태",
                <StatusBadge
                  domain="reservation"
                  status={detail.status}
                />,
              ],
            ]}
          />
        </Dialog>
      )}
      {action && (
        <ActionDialog
          action={action}
          onClose={() => setAction(null)}
        />
      )}
    </section>
  );
}

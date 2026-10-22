import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import {
  getGetLessonQueryKey,
  getGetLessonsInfiniteQueryKey,
  getGetMyPassesQueryKey,
  getGetMyReservationsQueryKey,
  useCancelReservation,
  useGetMyReservations,
  useGetReservationPolicy,
  type Reservation,
} from "@/api/generated/api";
import { Dialog } from "@/components/ui/Dialog";
import { PageTitle } from "@/components/ui/PageTitle";
import { Pagination, PAGE_SIZE } from "@/components/ui/Pagination";
import { MenuPanel, MenuEmpty } from "@/components/layout/MenuPanel";
import { SortHeaderButton } from "@/components/ui/SortHeaderButton";
import { Button } from "@/components/ui/button";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { seoulDate, seoulTime } from "@/features/dashboard/model";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";

const lessonDate = (item: Reservation) =>
  `${seoulDate(item.lesson.startAt)} ${seoulTime(item.lesson.startAt)}`;

export function ReservationsPage({ canCancel }: { canCancel: boolean }) {
  const client = useQueryClient();
  const [tab, setTab] = useState<"reserved" | "all">("reserved");
  const [page, setPage] = useState(1);
  const [ascending, setAscending] = useState(true);
  const [cancelTarget, setCancelTarget] = useState<Reservation | null>(null);
  const query = useGetMyReservations({
    page,
    size: PAGE_SIZE,
    status: tab === "reserved" ? "RESERVED" : undefined,
    order: ascending ? "ASC" : "DESC",
  });
  const cancel = useCancelReservation();
  const policy = useGetReservationPolicy();
  const rows = query.data?.content ?? [];
  const pageInfo = query.data?.pageInfo;

  const confirmCancel = async () => {
    if (!cancelTarget) return;
    try {
      await cancel.mutateAsync({ reservationId: cancelTarget.id });
      await Promise.all([
        client.invalidateQueries({ queryKey: getGetMyReservationsQueryKey() }),
        client.invalidateQueries({ queryKey: getGetMyPassesQueryKey() }),
        client.invalidateQueries({ queryKey: getGetLessonsInfiniteQueryKey() }),
        client.invalidateQueries({ queryKey: getGetLessonQueryKey(cancelTarget.lesson.id) }),
      ]);
      setCancelTarget(null);
      setPage(1);
      toast.success("예약이 취소되고 이용권 횟수가 복구되었습니다.");
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "예약 취소에 실패했습니다.");
    }
  };

  return (
    <div className="menu-page member-reservations-page">
      <PageTitle
        title="내 예약·출결"
        description="예약 완료와 지난 수업의 출결·취소 내역을 확인합니다."
      />
      <Tabs
        value={tab}
        onValueChange={(value) => {
          setTab(value as "reserved" | "all");
          setPage(1);
        }}
        className="menu-tabs"
      >
        <TabsList>
          <TabsTrigger value="reserved">예약 완료</TabsTrigger>
          <TabsTrigger value="all">전체 내역</TabsTrigger>
        </TabsList>
        {["reserved", "all"].map((value) => (
          <TabsContent
            value={value}
            key={value}
          >
            <div className="menu-stack">
              <MenuPanel variant="table">
                {query.isPending ? (
                  <MenuEmpty
                    title="예약을 불러오는 중입니다"
                    description="잠시만 기다려 주세요."
                  />
                ) : query.isError ? (
                  <MenuEmpty
                    title="예약을 불러오지 못했습니다"
                    description="다시 시도해 주세요."
                  />
                ) : rows.length ? (
                  <Table
                    variant="management"
                    className="member-reservations-table"
                    aria-label={value === "reserved" ? "예약 완료 목록" : "전체 예약 내역"}
                  >
                    <TableHeader>
                      <TableRow>
                        <TableHead>수업</TableHead>
                        <TableHead aria-sort={ascending ? "ascending" : "descending"}>
                          <SortHeaderButton
                            label="수업 일시"
                            direction={ascending ? "asc" : "desc"}
                            onClick={() => {
                              setAscending((current) => !current);
                              setPage(1);
                            }}
                          />
                        </TableHead>
                        <TableHead>예약 일시</TableHead>
                        <TableHead>강사</TableHead>
                        <TableHead>상태</TableHead>
                        <TableHead>사용 이용권</TableHead>
                        {canCancel && <TableHead>예약 취소</TableHead>}
                      </TableRow>
                    </TableHeader>
                    <TableBody>
                      {rows.map((item) => (
                        <TableRow key={item.id}>
                          <TableCell>
                            <strong>{item.lesson.title}</strong>
                          </TableCell>
                          <TableCell>{lessonDate(item)}</TableCell>
                          <TableCell>
                            {seoulDate(item.createdAt)} {seoulTime(item.createdAt)}
                          </TableCell>
                          <TableCell>{item.lesson.instructor?.name ?? "-"}</TableCell>
                          <TableCell>
                            <StatusBadge
                              domain="reservation"
                              status={item.status}
                            />
                          </TableCell>
                          <TableCell>{item.pass.passProduct.name}</TableCell>
                          {canCancel && (
                            <TableCell>
                              {item.status === "RESERVED" &&
                                Date.parse(item.lesson.startAt) >
                                  Date.now() +
                                    (policy.data?.cancelDeadlineHours ?? 0) * 3600000 && (
                                  <Button
                                    variant="warning"
                                    disabled={cancel.isPending}
                                    onClick={() => setCancelTarget(item)}
                                  >
                                    예약 취소
                                  </Button>
                                )}
                            </TableCell>
                          )}
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                ) : (
                  <MenuEmpty
                    title={value === "reserved" ? "예약한 수업이 없습니다" : "예약 내역이 없습니다"}
                    description="수업 목록에서 예약 가능한 수업을 확인하세요."
                  />
                )}
              </MenuPanel>
              {query.isError && (
                <Button
                  variant="outline"
                  onClick={() => void query.refetch()}
                >
                  다시 시도
                </Button>
              )}
              <Pagination
                total={pageInfo?.totalElements ?? 0}
                page={page}
                pageSize={PAGE_SIZE}
                totalPages={Math.max(1, pageInfo?.totalPages ?? 1)}
                onPageChange={setPage}
              />
            </div>
          </TabsContent>
        ))}
      </Tabs>
      {cancelTarget && (
        <Dialog
          dialog={{
            title: "예약 취소",
            body: `${cancelTarget.lesson.title} · ${lessonDate(cancelTarget)}`,
            details: [
              { label: "수업", value: cancelTarget.lesson.title },
              { label: "일시", value: lessonDate(cancelTarget) },
            ],
            notice: "취소하면 예약이 해제되고 이용권 1회가 복구됩니다.",
          }}
          onClose={() => {
            if (!cancel.isPending) setCancelTarget(null);
          }}
          footerAction={
            <Button
              variant="warning"
              disabled={cancel.isPending}
              onClick={() => void confirmCancel()}
            >
              {cancel.isPending ? "취소 처리 중…" : "예약 취소하기"}
            </Button>
          }
        />
      )}
    </div>
  );
}

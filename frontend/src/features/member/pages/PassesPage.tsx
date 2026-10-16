import { PaginatedList } from "@/components/ui/PaginatedList";
import { passes } from "@/mocks/member";
import { passHistory } from "@/mocks/passHistory";
import { seoulDate, seoulTime } from "@/features/dashboard/model";
import { PageTitle } from "@/components/ui/PageTitle";
import { MenuEmpty, MenuPanel } from "@/components/layout/MenuPanel";
import { Button } from "@/components/ui/button";
import { StatusBadge } from "@/components/ui/StatusBadge";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import { PassUsage } from "@/features/member/PassUsage";

export function PassesPage({ onOpen }: { onOpen: (id: number) => void }) {
  return (
    <div className="menu-page">
      <PageTitle
        title="내 이용권"
        description="보유한 이용권과 남은 횟수, 이용 기간을 확인합니다."
      />
      <PaginatedList items={passes}>
        {(pageItems) => (
          <div className="menu-pass-list">
            {pageItems.map((pass) => (
              <MenuPanel
                key={pass.id}
                variant="pass"
                title={pass.name}
                action={
                  <StatusBadge
                    domain="pass"
                    status="ACTIVE"
                  />
                }
              >
                <div className="menu-stack">
                  <PassUsage pass={pass} />
                  <div className="menu-actions">
                    <Button
                      variant="outline"
                      onClick={() => onOpen(pass.id)}
                    >
                      이용권 상세
                    </Button>
                  </div>
                </div>
              </MenuPanel>
            ))}
          </div>
        )}
      </PaginatedList>
    </div>
  );
}

export function PassDetailPage({ passId, onBack }: { passId: number; onBack: () => void }) {
  const pass = passes.find((item) => item.id === passId) ?? passes[0];
  const history = passHistory
    .filter((item) => item.passId === pass.id)
    .sort((a, b) => Date.parse(b.occurredAt) - Date.parse(a.occurredAt) || b.id - a.id);
  return (
    <div className="menu-page">
      <PageTitle
        title="이용권 상세"
        description={pass.name}
        action={
          <Button
            variant="outline"
            onClick={onBack}
          >
            목록으로
          </Button>
        }
      />
      <MenuPanel
        title={pass.name}
        action={
          <StatusBadge
            domain="pass"
            status="ACTIVE"
          />
        }
      >
        <div className="menu-pass-detail">
          <PassUsage pass={pass} />
          <p className="menu-note">
            만료일 당일까지 이용할 수 있습니다. 차감 횟수에는 예약과 관리자 조정이 포함되어 실제
            출석 횟수와 다를 수 있습니다.
          </p>
        </div>
      </MenuPanel>
      <MenuPanel
        variant="table"
        title="횟수 차감·복구 내역"
      >
        {history.length ? (
          <PaginatedList
            key={pass.id}
            items={history}
          >
            {(pageItems) => (
              <Table
                variant="management"
                aria-label={`${pass.name} 횟수 차감·복구 내역`}
              >
                <TableHeader>
                  <TableRow>
                    <TableHead scope="col">처리 일시</TableHead>
                    <TableHead scope="col">내용</TableHead>
                    <TableHead scope="col">수업</TableHead>
                    <TableHead scope="col">횟수 변동</TableHead>
                    <TableHead scope="col">처리 후 잔여</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {pageItems.map((item) => (
                    <TableRow key={item.id}>
                      <TableCell>
                        <time dateTime={item.occurredAt}>
                          {seoulDate(item.occurredAt)} {seoulTime(item.occurredAt)}
                        </time>
                      </TableCell>
                      <TableCell>
                        {item.type === "RESERVATION" ? "수업 예약 차감" : "예약 취소 복구"}
                      </TableCell>
                      <TableCell>
                        <strong>{item.lesson}</strong>
                        <p className="menu-muted">
                          <time dateTime={item.lessonStartAt}>
                            {seoulDate(item.lessonStartAt)} {seoulTime(item.lessonStartAt)}
                          </time>
                        </p>
                      </TableCell>
                      <TableCell>
                        {item.change > 0 ? "+" : ""}
                        {item.change}회
                      </TableCell>
                      <TableCell>{item.remaining}회</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            )}
          </PaginatedList>
        ) : (
          <MenuEmpty
            title="횟수 변경 내역이 없습니다"
            description="수업 예약이나 취소로 이용권 횟수가 변경되면 표시됩니다."
          />
        )}
      </MenuPanel>
    </div>
  );
}

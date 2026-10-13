import { useState } from "react";
import { reservations } from "@/mocks/member";
import type { DialogState } from "@/app/routes";
import { PageTitle } from "@/components/ui/PageTitle";
import { Pagination } from "@/components/ui/Pagination";
import { MenuPanel, MenuEmpty } from "@/components/layout/MenuPanel";
import { Button } from "@/components/ui/button";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { Tabs, TabsList, TabsTrigger, TabsContent } from "@/components/ui/tabs";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";

type Reservation = (typeof reservations)[number];
const status = (item: Reservation) => item.state === "출석 완료" ? "ATTENDED" : "RESERVED";
export function reservationDialog(id: number): NonNullable<DialogState> {
  const item = reservations.find(item => item.id === id) ?? reservations[0];
  return { title: "예약 상세", body: `${item.lesson}\n예약 번호: #${item.id}\n수업 일시: ${item.date}\n담당 강사: ${item.teacher}\n사용 이용권: ${item.pass}\n출결 상태: ${item.state}` };
}

export function ReservationsPage({ onOpen, onDialog, canCancel }: { onOpen: (id: number) => void; onDialog: (dialog: DialogState) => void; canCancel: boolean }) {
  const [tab, setTab] = useState("reserved");
  const rows = reservations.filter(item => tab === "reserved" ? item.state === "예약 완료" : tab === "history" ? item.state !== "예약 완료" : false);
  return <div className="menu-page"><PageTitle title="내 예약·출결" description="예약 완료와 대기 신청, 지난 수업의 출결 내역을 확인합니다." />
    <Tabs value={tab} onValueChange={setTab} className="menu-tabs"><TabsList><TabsTrigger value="reserved">예약 완료</TabsTrigger><TabsTrigger value="waiting">대기 신청</TabsTrigger><TabsTrigger value="history">지난 수업·취소</TabsTrigger></TabsList>
      {["reserved", "waiting", "history"].map(value => <TabsContent value={value} key={value}>
        <div className="menu-stack"><div className="menu-results"><strong>전체 {rows.length}건</strong><span>{value === "waiting" ? "대기는 예약 확정이 아닙니다." : "본인의 예약·출결 내역만 표시됩니다."}</span></div>
          <MenuPanel variant="table">
            {rows.length ? <Table variant="management"><TableHeader><TableRow><TableHead>수업</TableHead><TableHead>수업 일시</TableHead><TableHead>강사</TableHead><TableHead>상태</TableHead><TableHead>사용 이용권</TableHead><TableHead>내역</TableHead></TableRow></TableHeader><TableBody>{rows.map(item => <TableRow key={item.id}><TableCell><strong>{item.lesson}</strong><p className="menu-muted">예약 #{item.id}</p></TableCell><TableCell>{item.date}</TableCell><TableCell>{item.teacher}</TableCell><TableCell><StatusBadge domain="reservation" status={status(item)} /></TableCell><TableCell>{item.pass}</TableCell><TableCell><div className="menu-actions"><Button variant="ghost" onClick={() => onOpen(item.id)}>상세 보기</Button>{item.state === "예약 완료" && canCancel && <Button variant="destructive" onClick={() => onDialog({ title: "예약 취소", body: `${item.lesson} · ${item.date}\n취소하면 수업 예약이 해제됩니다.`, action: "예약 취소하기", destructive: true })}>예약 취소</Button>}</div></TableCell></TableRow>)}</TableBody></Table> : <MenuEmpty title={value === "waiting" ? "대기 신청한 수업이 없습니다" : value === "history" ? "지난 수업·취소 기록이 없습니다" : "예약한 수업이 없습니다"} description="수업 목록에서 수업과 신청 가능 여부를 확인하세요." />}
          </MenuPanel><Pagination total={rows.length} />
        </div>
      </TabsContent>)}
    </Tabs>
  </div>;
}

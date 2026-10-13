import { passes } from "@/mocks/member";
import { PageTitle } from "@/components/ui/PageTitle";
import { MenuPanel } from "@/components/layout/MenuPanel";
import { Button } from "@/components/ui/button";
import { Progress } from "@/components/ui/progress";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";

type Pass = (typeof passes)[number];
function PassBalance({ pass }: { pass: Pass }) {
  const used = pass.total - pass.remaining;
  return <div className="menu-pass-balance">
    <span className="menu-muted">총 {pass.total}회 중</span><p><strong>{pass.remaining}회</strong> 남음</p>
    <Progress value={pass.total > 0 ? used / pass.total * 100 : 0} aria-label={`총 ${pass.total}회 중 사용 ${used}회, 남은 ${pass.remaining}회`} />
    <div className="home-pass-legend"><span><i className="home-pass-legend-used" aria-hidden="true" />사용 {used}회</span><span><i className="home-pass-legend-remaining" aria-hidden="true" />남음 {pass.remaining}회</span></div>
    <Table variant="management" className="menu-count-table" aria-label={`${pass.name} 횟수 요약`}><TableHeader><TableRow><TableHead>총 횟수</TableHead><TableHead>사용 횟수</TableHead><TableHead>남은 횟수</TableHead></TableRow></TableHeader><TableBody><TableRow><TableCell>{pass.total}회</TableCell><TableCell>{used}회</TableCell><TableCell><strong>{pass.remaining}회</strong></TableCell></TableRow></TableBody></Table>
  </div>;
}

export function PassesPage({ onOpen }: { onOpen: (id: number) => void }) {
  return <div className="menu-page"><PageTitle title="내 이용권" description="보유한 이용권과 남은 횟수, 이용 기간을 확인합니다." />
    <div className="menu-results"><strong>보유 이용권 {passes.length}개</strong><span>현재 사용 가능한 이용권</span></div>
    <div className="menu-pass-list">{passes.map(pass => <MenuPanel key={pass.id} variant="pass" title={pass.name} action={<StatusBadge domain="pass" status="ACTIVE" />}>
      <div className="menu-stack"><PassBalance pass={pass} /><dl className="menu-facts"><div><dt>담당 강사</dt><dd>{pass.teacher}</dd></div><div><dt>이용 기간</dt><dd>{pass.start} ~ {pass.end}</dd></div></dl><div className="menu-actions"><Button variant="outline" onClick={() => onOpen(pass.id)}>이용권 상세</Button></div></div>
    </MenuPanel>)}</div>
  </div>;
}

export function PassDetailPage({ passId, onBack }: { passId: number; onBack: () => void }) {
  const pass = passes.find(item => item.id === passId) ?? passes[0];
  return <div className="menu-page"><PageTitle title="이용권 상세" description={pass.name} action={<Button variant="outline" onClick={onBack}>목록으로</Button>} />
    <MenuPanel title={pass.name} action={<StatusBadge domain="pass" status="ACTIVE" />}>
      <div className="menu-pass-detail"><PassBalance pass={pass} /><div className="menu-stack"><Table variant="management" className="menu-info-table"><TableBody>{[["담당 강사", pass.teacher], ["시작일", pass.start], ["만료일", `${pass.end} · 당일까지 이용`], ["이용권 상태", pass.state]].map(([label, value]) => <TableRow key={label}><TableHead scope="row">{label}</TableHead><TableCell>{value}</TableCell></TableRow>)}</TableBody></Table><p className="menu-note">차감 횟수에는 예약과 관리자 조정이 포함됩니다. 실제 출석 횟수와 다를 수 있습니다.</p></div></div>
    </MenuPanel>
  </div>;
}

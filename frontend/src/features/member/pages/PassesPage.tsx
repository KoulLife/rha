import { passes } from "../../../mocks/member";
import { PageTitle } from "../../../components/ui/PageTitle";

export function PassesPage({ onOpen }: { onOpen: (id: number) => void }) {
  return <><PageTitle title="내 이용권" description="보유한 이용권과 남은 횟수, 이용 기간을 확인합니다." /><div className="pass-list">{passes.map((pass) => <PassCard key={pass.id} pass={pass} onOpen={() => onOpen(pass.id)} />)}</div></>;
}

function PassCard({ pass, onOpen }: { pass: typeof passes[number]; onOpen: () => void }) {
  const used = pass.total - pass.remaining;
  return <article className="card pass-card"><div className="panel-heading"><div><span className="eyebrow">이용권</span><h2>{pass.name}</h2></div><span className="badge success">{pass.state}</span></div><div className="pass-balance"><strong>{pass.remaining}회 <small>/ {pass.total}회 남음</small></strong><progress value={used} max={pass.total} /></div><dl className="facts compact"><div><dt>담당 강사</dt><dd>{pass.teacher}</dd></div><div><dt>이용 기간</dt><dd>{pass.start} ~ {pass.end}</dd></div></dl><div className="card-actions"><button className="button outline" type="button" onClick={onOpen}>이용권 상세</button></div></article>;
}

export function PassDetailPage({ passId, onBack }: { passId: number; onBack: () => void }) {
  const pass = passes.find((item) => item.id === passId) ?? passes[0];
  return <><PageTitle title="이용권 상세" description={pass.name} action={<button className="button outline" type="button" onClick={onBack}>목록으로</button>} /><section className="card detail-card pass-detail-card"><div className="panel-heading"><div><span className="eyebrow">내 이용권</span><h2>{pass.name}</h2></div><span className="badge success">{pass.state}</span></div><div className="pass-detail-body"><div className="large-balance"><span className="balance-label">잔여 횟수</span><strong>{pass.remaining}회</strong><span>/ 총 {pass.total}회</span><progress value={pass.total - pass.remaining} max={pass.total} /></div><div className="pass-detail-info"><dl className="facts"><div><dt>담당 강사</dt><dd>{pass.teacher}</dd></div><div><dt>시작일</dt><dd>{pass.start}</dd></div><div><dt>만료일</dt><dd>{pass.end} · 당일까지 이용</dd></div><div><dt>이용권 상태</dt><dd>{pass.state}</dd></div></dl><p className="hint">차감 횟수에는 예약과 관리자 조정이 포함됩니다. 실제 출석 횟수와 다를 수 있습니다.</p></div></div></section></>;
}


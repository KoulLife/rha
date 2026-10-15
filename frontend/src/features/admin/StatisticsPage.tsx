import { useAllMembers } from './queries';
import * as api from '@/api/generated/api';
import { MenuPanel } from '@/components/layout/MenuPanel';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip';
import { AdminPage, QueryState, money } from './AdminUI';

const seoulMonth = () => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit' }).format(new Date());
const shiftMonth = (month: string, offset: number) => {
  const [year, number] = month.split('-').map(Number);
  return new Date(Date.UTC(year, number - 1 + offset, 1)).toISOString().slice(0, 7);
};
const monthEnd = (month: string) => {
  const [year, number] = month.split('-').map(Number);
  return new Date(Date.UTC(year, number, 0)).toISOString().slice(0, 10);
};
const localStart = (month: string) => new Date(`${month}-01T00:00:00+09:00`).toISOString();
const count = (query: { data?: { pageInfo: { totalElements: number } } }) => query.data?.pageInfo.totalElements;
const sales = (query: { data?: api.PassPaymentPageResponse }) => query.data && query.data.pageInfo.totalPages <= 1
  ? query.data.content.filter(payment => !payment.refunded).reduce((sum, payment) => sum + payment.amount, 0) : undefined;
const percent = (current?: number, previous?: number) => current === undefined || previous === undefined || previous === 0
  ? '—' : `${((current - previous) / previous * 100).toFixed(1)}%`;

type Bar = { label: string; value?: number };

function StatisticCard({ title, value, note }: { title: string; value: string | number; note: string }) {
  return <Tooltip>
    <TooltipTrigger asChild>
      <div className="admin-stat-card" tabIndex={0}>
        <MenuPanel title={title}><p className="admin-metric">{value}</p></MenuPanel>
      </div>
    </TooltipTrigger>
    <TooltipContent side="top">{note}</TooltipContent>
  </Tooltip>;
}

function ComparisonBars({ title, bars, format = value => value.toLocaleString('ko-KR') }: {
  title: string; bars: Bar[]; format?: (value: number) => string;
}) {
  const max = Math.max(1, ...bars.map(bar => bar.value ?? 0));
  return <MenuPanel title={title}>
    <div className="admin-chart" role="img" aria-label={`${title}: ${bars.map(bar => `${bar.label} ${bar.value === undefined ? '집계 불가' : format(bar.value)}`).join(', ')}`}>
      {bars.map(bar => <div className="admin-chart-row" key={bar.label}>
        <div className="admin-chart-row-heading"><span>{bar.label}</span><strong>{bar.value === undefined ? '—' : format(bar.value)}</strong></div>
        <div className="admin-chart-track"><span className="admin-chart-bar" style={{ width: `${bar.value === undefined ? 0 : bar.value / max * 100}%` }} /></div>
      </div>)}
    </div>
  </MenuPanel>;
}

export function StatisticsPage() {
  const month = seoulMonth(), from = localStart(month), to = localStart(shiftMonth(month, 1));
  const members = useAllMembers({ role: 'MEMBER', size: 100 });
  const passes = api.useGetPasses({ createdFrom: from, createdTo: to, size: 1 });
  const lessons = api.useGetLessons({ from, to, size: 1 });
  const reserved = api.useGetReservations({ lessonFrom: from, lessonTo: to, status: 'RESERVED', size: 1 });
  const canceled = api.useGetReservations({ lessonFrom: from, lessonTo: to, status: 'CANCELED', size: 1 });
  const attended = api.useGetReservations({ lessonFrom: from, lessonTo: to, status: 'ATTENDED', size: 1 });
  const noShow = api.useGetReservations({ lessonFrom: from, lessonTo: to, status: 'NO_SHOW', size: 1 });
  const registered = members.data && members.data.pageInfo.totalPages <= 1
    ? members.data.content.filter(member => member.createdAt >= from && member.createdAt < to).length : undefined;
  const completed = (count(attended) ?? 0) + (count(noShow) ?? 0);
  const noShowRate = completed > 0 ? `${((count(noShow) ?? 0) / completed * 100).toFixed(1)}%` : '—';
  const queries = [members, passes, lessons, reserved, canceled, attended, noShow];
  return <AdminPage title="운영 통계" description={`${month}에 시작하는 수업과 회원·이용권 운영 현황입니다.`}>
    <QueryState query={{ isPending: queries.some(query => query.isPending), isError: queries.some(query => query.isError), error: queries.find(query => query.isError)?.error, refetch: () => Promise.all(queries.map(query => query.refetch())) }}>
      <TooltipProvider><div className="admin-stats-grid">
        <StatisticCard title="회원 등록" value={registered ?? '—'} note={`이번 달 등록 회원${registered === undefined ? ' · 집계 불가' : ''}`} />
        <StatisticCard title="이용권 발급" value={count(passes) ?? '—'} note="이번 달 발급 건수" />
        <StatisticCard title="수업 운영" value={count(lessons) ?? '—'} note="이번 달 시작하는 수업" />
        <StatisticCard title="노쇼율" value={noShowRate} note="이번 달 수업의 노쇼 ÷ (출석 + 노쇼)" />
      </div></TooltipProvider>
      <ComparisonBars title="수업별 예약 상태" bars={[
        { label: '예약 완료', value: count(reserved) },
        { label: '출석', value: count(attended) },
        { label: '노쇼', value: count(noShow) },
        { label: '취소', value: count(canceled) },
      ]} />
      <p className="menu-note">예약 상태는 이번 달 수업에 연결된 현재 기록 기준입니다. 신청일·취소일 기준 발생 건수와 재등록 건수는 별도 집계 API가 필요합니다.</p>
    </QueryState>
  </AdminPage>;
}

export function SalesStatisticsPage() {
  const month = seoulMonth(), last = shiftMonth(month, -1), lastYear = shiftMonth(month, -12);
  const current = api.useGetPayments({ paidFrom: `${month}-01`, paidTo: monthEnd(month), size: 100 });
  const previous = api.useGetPayments({ paidFrom: `${last}-01`, paidTo: monthEnd(last), size: 100 });
  const yearly = api.useGetPayments({ paidFrom: `${lastYear}-01`, paidTo: monthEnd(lastYear), size: 100 });
  const queries = [current, previous, yearly];
  const thisSales = sales(current), lastSales = sales(previous), yearSales = sales(yearly);
  return <AdminPage title="매출 통계" description="오프라인 결제 기록의 월별 합계와 비교입니다.">
    <QueryState query={{ isPending: queries.some(query => query.isPending), isError: queries.some(query => query.isError), error: queries.find(query => query.isError)?.error, refetch: () => Promise.all(queries.map(query => query.refetch())) }}>
      <TooltipProvider><div className="admin-stats-grid">
        <StatisticCard title="이번 달 매출" value={thisSales === undefined ? '—' : money(thisSales)} note={`${month} 결제 기록 합계`} />
        <StatisticCard title="지난달 대비" value={percent(thisSales, lastSales)} note={`${last} 대비 증감률`} />
        <StatisticCard title="지난해 같은 달 대비" value={percent(thisSales, yearSales)} note={`${lastYear} 대비 증감률`} />
      </div></TooltipProvider>
      <ComparisonBars title="월별 매출 비교" format={money} bars={[
        { label: lastYear, value: yearSales },
        { label: last, value: lastSales },
        { label: month, value: thisSales },
      ]} />
      <p className="menu-note">환불 표시된 결제 기록은 제외합니다. 각 월의 전체 결제 기록을 합산하며, 비교 월의 매출이 0원이면 증감률을 표시하지 않습니다.</p>
    </QueryState>
  </AdminPage>;
}

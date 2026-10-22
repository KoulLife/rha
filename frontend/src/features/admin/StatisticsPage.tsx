import { useAllMembers } from "./queries";
import * as api from "@/api/generated/api";
import { MenuPanel } from "@/components/layout/MenuPanel";
import { AdminPage, QueryState, money } from "./AdminUI";

const seoulMonth = () =>
  new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "2-digit",
  }).format(new Date());
const shiftMonth = (month: string, offset: number) => {
  const [year, number] = month.split("-").map(Number);
  return new Date(Date.UTC(year, number - 1 + offset, 1)).toISOString().slice(0, 7);
};
const monthEnd = (month: string) => {
  const [year, number] = month.split("-").map(Number);
  return new Date(Date.UTC(year, number, 0)).toISOString().slice(0, 10);
};
const localStart = (month: string) => new Date(`${month}-01T00:00:00+09:00`).toISOString();
const count = (query: { data?: { pageInfo: { totalElements: number } } }) =>
  query.data?.pageInfo.totalElements;
const sales = (query: { data?: api.PassPaymentPageResponse }) =>
  query.data && query.data.pageInfo.totalPages <= 1
    ? query.data.content
        .filter((payment) => !payment.refunded)
        .reduce((sum, payment) => sum + payment.amount, 0)
    : undefined;
const number = (value: number) => value.toLocaleString("ko-KR");

type Bar = { label: string; value?: number; tone: 1 | 2 | 3 | 4 };

function StatisticCard({
  title,
  value,
  note,
  featured = false,
}: {
  title: string;
  value: string | number;
  note: string;
  featured?: boolean;
}) {
  return (
    <MenuPanel className="admin-stat-card">
      <div
        className="admin-stat-card-content"
        data-featured={featured}
      >
        <span className="admin-stat-label">{title}</span>
        <strong className="admin-stat-value">{value}</strong>
        <span className="admin-stat-note">{note}</span>
      </div>
    </MenuPanel>
  );
}

function ComparisonBars({ title, bars }: { title: string; bars: Bar[] }) {
  const max = Math.max(1, ...bars.map((bar) => bar.value ?? 0));
  return (
    <MenuPanel
      title={title}
      className="admin-stats-panel"
    >
      <p className="admin-chart-caption">항목별 건수를 비교합니다.</p>
      <div className="admin-chart">
        {bars.map((bar) => (
          <div
            className="admin-chart-row"
            key={bar.label}
            data-tone={bar.tone}
          >
            <div className="admin-chart-row-heading">
              <span>{bar.label}</span>
              <strong>{bar.value === undefined ? "집계 불가" : `${number(bar.value)}건`}</strong>
            </div>
            <div
              className="admin-chart-track"
              aria-hidden="true"
            >
              <span
                className="admin-chart-bar"
                style={{ width: `${bar.value === undefined ? 0 : (bar.value / max) * 100}%` }}
              />
            </div>
          </div>
        ))}
      </div>
    </MenuPanel>
  );
}

function ReservationDonut({ bars }: { bars: Bar[] }) {
  const complete = bars.every((bar) => bar.value !== undefined);
  const total = complete ? bars.reduce((sum, bar) => sum + (bar.value ?? 0), 0) : undefined;
  let end = 0;
  const segments = bars.map((bar) => {
    const start = end;
    end += total ? ((bar.value ?? 0) / total) * 100 : 0;
    return `var(--chart-${bar.tone}) ${start}% ${end}%`;
  });
  return (
    <MenuPanel
      title="예약 상태 분포"
      className="admin-stats-panel"
    >
      <p className="admin-chart-caption">이번 달 수업에 연결된 예약의 현재 상태입니다.</p>
      <div className="admin-donut-layout">
        <div
          className="admin-donut"
          style={{
            background: total ? `conic-gradient(${segments.join(", ")})` : "var(--secondary)",
          }}
          aria-hidden="true"
        >
          <div className="admin-donut-center">
            <span>전체 예약</span>
            <strong>{total === undefined ? "—" : number(total)}</strong>
            <span>건</span>
          </div>
        </div>
        <div className="admin-donut-legend">
          {bars.map((bar) => (
            <div
              className="admin-donut-legend-row"
              key={bar.label}
              data-tone={bar.tone}
            >
              <span className="admin-donut-legend-label">
                <span
                  className="admin-donut-swatch"
                  aria-hidden="true"
                />
                {bar.label}
              </span>
              <strong>{bar.value === undefined ? "—" : `${number(bar.value)}건`}</strong>
              <span className="admin-donut-percent">
                {total && bar.value !== undefined
                  ? `${((bar.value / total) * 100).toFixed(1)}%`
                  : "—"}
              </span>
            </div>
          ))}
        </div>
      </div>
    </MenuPanel>
  );
}

function SalesColumns({ bars }: { bars: (Bar & { period: string })[] }) {
  const max = Math.max(1, ...bars.map((bar) => bar.value ?? 0));
  return (
    <MenuPanel
      title="월별 매출 비교"
      className="admin-stats-panel"
    >
      <p className="admin-chart-caption">환불 표시된 결제 기록을 제외한 월별 합계입니다.</p>
      <div className="admin-sales-chart">
        {bars.map((bar) => (
          <div
            className="admin-sales-chart-column"
            key={bar.label}
            data-tone={bar.tone}
          >
            <strong className="admin-sales-chart-value">
              {bar.value === undefined ? "집계 불가" : money(bar.value)}
            </strong>
            <div
              className="admin-sales-chart-track"
              aria-hidden="true"
            >
              <span style={{ height: `${((bar.value ?? 0) / max) * 100}%` }} />
            </div>
            <span className="admin-sales-chart-label">{bar.label}</span>
            <span className="admin-sales-chart-period">{bar.period}</span>
          </div>
        ))}
      </div>
    </MenuPanel>
  );
}

export function StatisticsPage() {
  const month = seoulMonth(),
    from = localStart(month),
    to = localStart(shiftMonth(month, 1));
  const members = useAllMembers({ role: "MEMBER", size: 100 });
  const passes = api.useGetPasses({ createdFrom: from, createdTo: to, size: 1 });
  const lessons = api.useGetLessons({ from, to, size: 1 });
  const reserved = api.useGetReservations({
    lessonFrom: from,
    lessonTo: to,
    status: "RESERVED",
    size: 1,
  });
  const canceled = api.useGetReservations({
    lessonFrom: from,
    lessonTo: to,
    status: "CANCELED",
    size: 1,
  });
  const attended = api.useGetReservations({
    lessonFrom: from,
    lessonTo: to,
    status: "ATTENDED",
    size: 1,
  });
  const noShow = api.useGetReservations({
    lessonFrom: from,
    lessonTo: to,
    status: "NO_SHOW",
    size: 1,
  });
  const registered =
    members.data && members.data.pageInfo.totalPages <= 1
      ? members.data.content.filter((member) => member.createdAt >= from && member.createdAt < to)
          .length
      : undefined;
  const passCount = count(passes),
    lessonCount = count(lessons);
  const completed = (count(attended) ?? 0) + (count(noShow) ?? 0);
  const noShowRate =
    completed > 0 ? `${(((count(noShow) ?? 0) / completed) * 100).toFixed(1)}%` : "—";
  const reservationBars: Bar[] = [
    { label: "예약 완료", value: count(reserved), tone: 1 },
    { label: "출석", value: count(attended), tone: 2 },
    { label: "노쇼", value: count(noShow), tone: 3 },
    { label: "취소", value: count(canceled), tone: 4 },
  ];
  const queries = [members, passes, lessons, reserved, canceled, attended, noShow];
  return (
    <AdminPage
      title="운영 통계"
      description={`${month}에 시작하는 수업과 회원·이용권 운영 현황입니다.`}
    >
      <QueryState
        query={{
          isPending: queries.some((query) => query.isPending),
          isError: queries.some((query) => query.isError),
          error: queries.find((query) => query.isError)?.error,
          refetch: () => Promise.all(queries.map((query) => query.refetch())),
        }}
      >
        <div className="admin-stats-layout">
          <div className="admin-stats-grid">
            <StatisticCard
              title="회원 등록"
              value={registered === undefined ? "—" : `${number(registered)}명`}
              note={
                registered === undefined ? "이번 달 등록 회원 · 집계 불가" : "이번 달 등록 회원"
              }
            />
            <StatisticCard
              title="이용권 발급"
              value={passCount === undefined ? "—" : `${number(passCount)}건`}
              note="이번 달 발급 건수"
            />
            <StatisticCard
              title="수업 운영"
              value={lessonCount === undefined ? "—" : `${number(lessonCount)}건`}
              note="이번 달 시작하는 수업"
            />
            <StatisticCard
              title="노쇼율"
              value={noShowRate}
              note="이번 달 수업의 노쇼 ÷ (출석 + 노쇼)"
            />
          </div>
          <div className="admin-stats-visuals">
            <ReservationDonut bars={reservationBars} />
            <ComparisonBars
              title="이번 달 활동 규모"
              bars={[
                { label: "회원 등록", value: registered, tone: 1 },
                { label: "이용권 발급", value: passCount, tone: 2 },
                { label: "수업 운영", value: lessonCount, tone: 3 },
              ]}
            />
          </div>
          <p className="admin-stats-footnote">
            예약 상태는 이번 달 수업에 연결된 현재 기록 기준입니다. 신청일·취소일 기준 발생 건수와
            재등록 건수는 별도 집계 API가 필요합니다.
          </p>
        </div>
      </QueryState>
    </AdminPage>
  );
}

export function SalesStatisticsPage() {
  const month = seoulMonth(),
    last = shiftMonth(month, -1),
    lastYear = shiftMonth(month, -12);
  const current = api.useGetPayments({
    paidFrom: `${month}-01`,
    paidTo: monthEnd(month),
    size: 100,
  });
  const previous = api.useGetPayments({
    paidFrom: `${last}-01`,
    paidTo: monthEnd(last),
    size: 100,
  });
  const yearly = api.useGetPayments({
    paidFrom: `${lastYear}-01`,
    paidTo: monthEnd(lastYear),
    size: 100,
  });
  const queries = [current, previous, yearly];
  const thisSales = sales(current),
    lastSales = sales(previous),
    yearSales = sales(yearly);
  const payments =
    current.data && current.data.pageInfo.totalPages <= 1
      ? current.data.content.filter((payment) => !payment.refunded)
      : undefined;
  const methodSales = payments?.reduce<Record<api.PaymentMethod, number>>(
    (totals, payment) => {
      totals[payment.method] += payment.amount;
      return totals;
    },
    { CARD: 0, CASH: 0, TRANSFER: 0, OTHER: 0 },
  );
  const methods: { label: string; method: api.PaymentMethod }[] = [
    { label: "카드 매출", method: "CARD" },
    { label: "현금 매출", method: "CASH" },
    { label: "계좌이체 매출", method: "TRANSFER" },
    { label: "기타 매출", method: "OTHER" },
  ];
  return (
    <AdminPage
      title="매출 통계"
      description="이번 달 결제 수단별 매출과 월별 매출을 확인합니다."
    >
      <QueryState
        query={{
          isPending: queries.some((query) => query.isPending),
          isError: queries.some((query) => query.isError),
          error: queries.find((query) => query.isError)?.error,
          refetch: () => Promise.all(queries.map((query) => query.refetch())),
        }}
      >
        <div className="admin-stats-layout">
          <div className="admin-stats-grid admin-sales-summary-grid">
            <StatisticCard
              title="이번 달 매출"
              value={thisSales === undefined ? "—" : money(thisSales)}
              note={
                payments === undefined
                  ? `${month} · 집계 불가`
                  : `결제 ${number(payments.length)}건 · 건당 평균 ${payments.length ? money(Math.round((thisSales ?? 0) / payments.length)) : "—"}`
              }
              featured
            />
            {methods.map(({ label, method }) => (
              <StatisticCard
                key={method}
                title={label}
                value={methodSales === undefined ? "—" : money(methodSales[method])}
                note={
                  payments === undefined
                    ? "환불 표시 제외 · 집계 불가"
                    : `환불 표시 제외 · 매출의 ${thisSales ? (((methodSales?.[method] ?? 0) / thisSales) * 100).toFixed(1) : "0.0"}%`
                }
              />
            ))}
          </div>
          <SalesColumns
            bars={[
              { label: "지난해 같은 달", period: lastYear, value: yearSales, tone: 3 },
              { label: "지난달", period: last, value: lastSales, tone: 2 },
              { label: "이번 달", period: month, value: thisSales, tone: 1 },
            ]}
          />
          <p className="admin-stats-footnote">
            매출은 결제일이 해당 월인 기록에서 환불 표시된 건을 제외한 금액입니다. 결제 기록이 한
            페이지를 넘으면 해당 월의 매출과 수단별 합계는 집계 불가로 표시합니다.
          </p>
        </div>
      </QueryState>
    </AdminPage>
  );
}

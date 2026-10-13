import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import "./dashboard-state.css";

export function DashboardLoading() {
  return <section className="dashboard-state" aria-busy="true" aria-label="홈 불러오는 중">
    <p role="status">홈 정보를 불러오고 있습니다.</p>
    <div className="home-metrics" aria-hidden="true">{Array.from({ length: 4 }, (_, index) => <Card key={index}><CardContent className="dashboard-state-lines"><Skeleton /><Skeleton /><Skeleton /></CardContent></Card>)}</div>
    <div className="home-columns" aria-hidden="true">
      <Card><CardHeader><Skeleton className="dashboard-state-title" /></CardHeader><CardContent className="dashboard-state-lines">{Array.from({ length: 6 }, (_, index) => <Skeleton className="dashboard-state-row" key={index} />)}</CardContent></Card>
      <div className="dashboard-state-side">
        <Card><CardHeader><Skeleton className="dashboard-state-title" /></CardHeader><CardContent className="dashboard-state-lines"><Skeleton className="dashboard-state-row" /><Skeleton className="dashboard-state-row" /></CardContent></Card>
        <Card><CardHeader><Skeleton className="dashboard-state-title" /></CardHeader><CardContent className="dashboard-state-lines"><Skeleton className="dashboard-state-row" /><Skeleton className="dashboard-state-row" /></CardContent></Card>
      </div>
    </div>
  </section>;
}

export function DashboardError({ status, onRetry, onLogin }: { status?: number; onRetry: () => void; onLogin: () => void }) {
  const expired = status === 401;
  const forbidden = status === 403;
  return <section className="dashboard-state">
    <h1>홈 정보를 확인할 수 없습니다</h1>
    <Alert variant="destructive">
      <AlertTitle>{expired ? "로그인이 만료되었습니다" : forbidden ? "접근 권한이 없습니다" : "정보를 불러오지 못했습니다"}</AlertTitle>
      <AlertDescription>{expired ? "다시 로그인한 뒤 이용해 주세요." : forbidden ? "이 계정으로 조회할 수 없는 정보입니다. 센터에 문의해 주세요." : "잠시 후 다시 시도해 주세요. 문제가 계속되면 센터에 문의해 주세요."}</AlertDescription>
    </Alert>
    <div>{expired || forbidden ? <Button onClick={onLogin}>로그인으로 돌아가기</Button> : <Button variant="outline" onClick={onRetry}>다시 시도</Button>}</div>
  </section>;
}

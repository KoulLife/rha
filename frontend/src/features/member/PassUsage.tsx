import type { CSSProperties } from "react";
import "./pass-usage.css";

const radius = 47;
const circumference = 2 * Math.PI * radius;

type PassSummary = {
  name: string;
  total: number;
  remaining: number;
  teacher: string;
  start: string;
  end: string;
};

export function PassUsage({ pass, compact = false }: { pass: PassSummary; compact?: boolean }) {
  const { name, total, remaining, teacher, start, end } = pass;
  const available = Math.max(0, Math.min(remaining, total));
  const used = Math.max(0, total - available);
  const fraction = total > 0 ? available / total : 0;
  const usedPercent = total > 0 ? Math.round((used / total) * 100) : 0;

  return (
    <div
      className="pass-usage"
      data-compact={compact}
    >
      <div className="pass-usage-visual">
        <div
          className="pass-usage-chart"
          role="progressbar"
          aria-label={`${name} 남은 횟수`}
          aria-valuemin={0}
          aria-valuemax={total}
          aria-valuenow={available}
        >
          <svg
            viewBox="0 0 120 120"
            aria-hidden="true"
            focusable="false"
          >
            <circle
              className="pass-usage-track"
              cx="60"
              cy="60"
              r={radius}
            />
            <circle
              className="pass-usage-value"
              cx="60"
              cy="60"
              r={radius}
              style={
                { strokeDasharray: `${fraction * circumference} ${circumference}` } as CSSProperties
              }
            />
          </svg>
          <div
            className="pass-usage-center"
            aria-hidden="true"
          >
            <strong>
              {available}
              <span>회</span>
            </strong>
            <span>남음</span>
          </div>
        </div>
        <p className="pass-usage-counts">
          총 {total}회 · 사용 {used}회
        </p>
      </div>
      <dl className="pass-usage-facts">
        <div>
          <dt>담당 강사</dt>
          <dd>{teacher}</dd>
        </div>
        <div>
          <dt>시작일</dt>
          <dd>{start}</dd>
        </div>
        <div>
          <dt>만료일</dt>
          <dd>{end}</dd>
        </div>
        <div>
          <dt>사용률</dt>
          <dd>
            <strong>{usedPercent}%</strong>
          </dd>
        </div>
      </dl>
    </div>
  );
}

import { Badge } from "@/components/ui/badge";
import "./status-badge.css";

const statuses = {
  lesson: {
    CREATED: ["개설", "neutral"],
    SCHEDULED: ["공개 예정", "warning"],
    UNPUBLISHED: ["비공개", "neutral"],
    AVAILABLE: ["예약 가능", "success"],
    WAITING: ["대기 신청 가능", "warning"],
    CLOSED: ["예약 마감", "destructive"],
    PUBLISHED: ["공개", "success"],
    CANCELED: ["수업 취소", "destructive"],
  },
  reservation: {
    RESERVED: ["예약 완료", "success"],
    ATTENDED: ["출석 완료", "success"],
    NO_SHOW: ["노쇼", "destructive"],
    CANCELED: ["예약 취소", "destructive"],
  },
  absence: {
    REQUESTED: ["승인 대기", "warning"],
    APPROVED: ["승인", "success"],
    REJECTED: ["반려", "destructive"],
    CANCELED: ["취소", "neutral"],
  },
  notification: { UNREAD: ["읽지 않음", "warning"], READ: ["읽음", "neutral"] },
  pass: {
    UPCOMING: ["시작 전", "warning"],
    ACTIVE: ["이용 중", "success"],
    PAUSED: ["홀딩 중", "warning"],
    EXPIRED: ["만료", "destructive"],
    REFUNDED: ["환불 완료", "destructive"],
  },
  member: {
    ACTIVE: ["활성", "success"],
    INACTIVE: ["비활성", "neutral"],
    WITHDRAWN: ["탈퇴", "neutral"],
    RESIGNED: ["퇴사", "destructive"],
  },
  payment: {
    UNPAID: ["미결제", "destructive"],
    PARTIAL: ["일부 결제", "warning"],
    PAID: ["완납", "success"],
    REFUNDED: ["환불", "destructive"],
  },
  settlement: {
    CALCULATED: ["계산 완료", "neutral"],
    REVIEW_REQUESTED: ["검토 요청", "warning"],
    CONFIRMED: ["확정", "success"],
    PAID: ["지급 기록 완료", "success"],
  },
  pause: {
    SCHEDULED: ["홀딩 예정", "warning"],
    ACTIVE: ["홀딩 중", "warning"],
    ENDED: ["종료", "neutral"],
    CANCELED: ["취소", "neutral"],
  },
  setting: { ACTIVE: ["활성", "success"], INACTIVE: ["비활성", "neutral"] },
} as const;

type Props = {
  [D in keyof typeof statuses]: { domain: D; status: keyof (typeof statuses)[D] };
}[keyof typeof statuses];
export function getStatusLabel(props: Props) {
  return (statuses[props.domain] as Record<string, readonly [string, string]>)[props.status][0];
}
export function StatusBadge(props: Props) {
  const [label, tone] = (statuses[props.domain] as Record<string, readonly [string, string]>)[
    props.status
  ];
  return (
    <Badge
      variant="outline"
      className={`status-badge-${tone}`}
    >
      {label}
    </Badge>
  );
}

export function AttendanceCount({
  status,
  count,
  unit = "",
}: {
  status: "ATTENDED" | "NO_SHOW";
  count: number;
  unit?: string;
}) {
  const [, tone] = statuses.reservation[status];
  return (
    <Badge
      variant="outline"
      className={`status-badge-${tone} status-badge-count`}
    >
      <span>{status === "ATTENDED" ? "출석" : "노쇼"}</span>
      <strong>
        {count}
        {unit}
      </strong>
    </Badge>
  );
}

import { Badge } from "@/components/ui/badge";
import "./status-badge.css";

const statuses = {
  lesson: { AVAILABLE: ["예약 가능", "success"], WAITING: ["대기 신청 가능", "warning"], CLOSED: ["예약 마감", "destructive"], PUBLISHED: ["공개", "success"], CANCELED: ["수업 취소", "destructive"] },
  reservation: { RESERVED: ["예약 완료", "success"], ATTENDED: ["출석 완료", "success"], NO_SHOW: ["노쇼", "destructive"], CANCELED: ["예약 취소", "destructive"] },
  absence: { REQUESTED: ["승인 대기", "warning"], APPROVED: ["승인", "success"], REJECTED: ["반려", "destructive"], CANCELED: ["취소", "neutral"] },
  notification: { UNREAD: ["읽지 않음", "warning"], READ: ["읽음", "neutral"] },
  pass: { ACTIVE: ["이용 중", "success"] },
} as const;

type Props = { domain: "absence"; status: keyof typeof statuses.absence } | { domain: "notification"; status: keyof typeof statuses.notification } | { domain: "lesson"; status: keyof typeof statuses.lesson } | { domain: "reservation"; status: keyof typeof statuses.reservation } | { domain: "pass"; status: keyof typeof statuses.pass };
export function StatusBadge(props: Props) {
  const [label, tone] = (statuses[props.domain] as Record<string, readonly [string, string]>)[props.status];
  return <Badge variant="outline" className={`status-badge-${tone}`}>{label}</Badge>;
}

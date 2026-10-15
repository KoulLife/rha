import { AttendanceCount } from "./StatusBadge";
import "./reservation-summary.css";

export function ReservationSummary({ total, reserved, attended, noShow }: { total: number; reserved: number; attended: number; noShow: number }) {
  return <div className="reservation-summary">
    <h2>예약자 {total}명</h2>
    <div className="reservation-summary-counts" aria-label="수업 예약·출결 인원">
      <span>예약 <strong>{reserved}명</strong></span>
      <AttendanceCount status="ATTENDED" count={attended} unit="명" />
      <AttendanceCount status="NO_SHOW" count={noShow} unit="명" />
    </div>
  </div>;
}

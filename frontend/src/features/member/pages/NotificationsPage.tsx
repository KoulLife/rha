import { notifications } from "../../../mocks/member";
import type { DialogState } from "../../../app/routes";
import { PageTitle } from "../../../components/ui/PageTitle";
import { Pagination } from "../../../components/ui/Pagination";

export function NotificationsPage({ onDialog }: { onDialog: (dialog: DialogState) => void }) {
  return <><PageTitle title="알림" description="내 알림을 확인하고 읽음으로 표시합니다." action={<button className="button outline" type="button">모든 알림 읽음으로 표시</button>} /><div className="result-line"><strong>전체 {notifications.length}건 · 읽지 않은 알림 2건</strong><label className="checkbox"><input type="checkbox" /> 읽지 않은 알림만 보기</label></div><section className="card table-wrap"><table><thead><tr><th>알림</th><th>분류</th><th>받은 시간</th><th>상태</th><th>상세</th></tr></thead><tbody>{notifications.map((notification) => <tr key={notification.id}><td><strong>{!notification.read && <span className="unread-dot" />} {notification.title}</strong><small>{notification.message}</small></td><td>{notification.category}</td><td>{notification.time}</td><td><span className={`badge ${notification.read ? "neutral" : "warning"}`}>{notification.read ? "읽음" : "읽지 않음"}</span></td><td><button className="text-button" type="button" onClick={() => onDialog({ title: "알림 상세", body: `${notification.title}\n${notification.message}\n받은 시간: ${notification.time}`, action: "확인" })}>상세 보기</button></td></tr>)}</tbody></table></section><Pagination /></>;
}


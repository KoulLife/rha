import { useState } from "react";
import { AppFooter } from "../components/layout/AppFooter";
import { AppHeader } from "../components/layout/AppHeader";
import { Breadcrumb } from "../components/ui/Breadcrumb";
import { Dialog } from "../components/ui/Dialog";
import { canAccessMenu, memberMenuItems, type Permission } from "../config/access";
import { currentUser } from "../mocks/member";
import { AccountPage } from "../features/member/pages/AccountPage";
import { LessonDetailPage, LessonsPage } from "../features/member/pages/LessonsPage";
import { NotificationsPage } from "../features/member/pages/NotificationsPage";
import { PassDetailPage, PassesPage } from "../features/member/pages/PassesPage";
import { ReservationDetailPage, ReservationsPage } from "../features/member/pages/ReservationsPage";
import type { DialogState, Screen } from "./routes";

const memberNavigation = memberMenuItems.filter((item) =>
  canAccessMenu(item, currentUser.role, currentUser.permissions),
);

function App() {
  const [screen, setScreen] = useState<Screen>("classes");
  const [selectedId, setSelectedId] = useState(101);
  const [dialog, setDialog] = useState<DialogState>(null);
  const [accountMenuOpen, setAccountMenuOpen] = useState(false);

  const go = (next: Screen, id?: number) => {
    if (id !== undefined) setSelectedId(id);
    setScreen(next);
    setAccountMenuOpen(false);
  };

  const can = (permission: Permission) => currentUser.permissions.has(permission);

  return (
    <div className="app-shell">
      <MemberSidebar screen={screen} onNavigate={go} />
      <main className="main-content">
        <AppHeader
          unreadCount={2}
          accountMenuOpen={accountMenuOpen}
          onNotificationClick={() => go("notifications")}
          onAccountToggle={() => setAccountMenuOpen((open) => !open)}
          onLogout={() => {
            setAccountMenuOpen(false);
            setDialog({ title: "로그아웃", body: "로그아웃하시겠습니까?", action: "로그아웃" });
          }}
        />
        <div className="content">
          <Breadcrumb screen={screen} />
          <MemberScreen
            screen={screen}
            selectedId={selectedId}
            onNavigate={go}
            onDialog={setDialog}
            canReserve={can("reservation.create")}
            canWaitlist={can("waitlist.create")}
            canCancel={can("reservation.cancel")}
            canUpdate={can("profile.update")}
            canChangePassword={can("password.change")}
          />
        </div>
        <AppFooter />
      </main>
      {dialog && <Dialog dialog={dialog} onClose={() => setDialog(null)} />}
    </div>
  );
}

function MemberSidebar({ screen, onNavigate }: { screen: Screen; onNavigate: (screen: Screen) => void }) {
  return (
    <aside className="sidebar">
      <div className="brand"><span className="brand-mark">R</span><strong>R.HA Pilates</strong></div>
      <div className="member-summary"><strong>홍길동</strong><span>회원</span></div>
      <nav aria-label="회원 메뉴">
        {memberNavigation.map((item) => {
          const detailParent: Record<string, Screen> = {
            "lesson-detail": "classes",
            "pass-detail": "passes",
            "reservation-detail": "reservations",
          };
          const active = screen === item.screen || detailParent[screen] === item.screen;
          return <button className={`sidebar-link ${active ? "active" : ""}`} type="button" key={item.screen} onClick={() => onNavigate(item.screen)}><span>{item.label}</span>{item.screen === "notifications" && <span className="count">2</span>}</button>;
        })}
      </nav>
    </aside>
  );
}

function MemberScreen({ screen, selectedId, onNavigate, onDialog, canReserve, canWaitlist, canCancel, canUpdate, canChangePassword }: { screen: Screen; selectedId: number; onNavigate: (screen: Screen, id?: number) => void; onDialog: (dialog: DialogState) => void; canReserve: boolean; canWaitlist: boolean; canCancel: boolean; canUpdate: boolean; canChangePassword: boolean }) {
  switch (screen) {
    case "classes": return <LessonsPage onOpen={(id) => onNavigate("lesson-detail", id)} canReserve={canReserve} canWaitlist={canWaitlist} />;
    case "lesson-detail": return <LessonDetailPage lessonId={selectedId} onBack={() => onNavigate("classes")} onDialog={onDialog} canReserve={canReserve} canWaitlist={canWaitlist} />;
    case "passes": return <PassesPage onOpen={(id) => onNavigate("pass-detail", id)} />;
    case "pass-detail": return <PassDetailPage passId={selectedId} onBack={() => onNavigate("passes")} />;
    case "reservations": return <ReservationsPage onOpen={(id) => onNavigate("reservation-detail", id)} onDialog={onDialog} canCancel={canCancel} />;
    case "reservation-detail": return <ReservationDetailPage reservationId={selectedId} onBack={() => onNavigate("reservations")} />;
    case "profile": return <AccountPage onDialog={onDialog} canUpdate={canUpdate} canChangePassword={canChangePassword} />;
    case "notifications": return <NotificationsPage onDialog={onDialog} />;
  }
}

export { App };


import { Collapsible } from "radix-ui";
import { ChevronRight } from "lucide-react";
import { useEffect, useState } from "react";
import type { CSSProperties } from "react";
import { AppFooter } from "../components/layout/AppFooter";
import { AppHeader } from "../components/layout/AppHeader";
import { BrandLogo } from "../components/layout/BrandLogo";
import { PageBreadcrumb } from "../components/layout/PageBreadcrumb";
import { Dialog } from "../components/ui/Dialog";
import { canAccessScreen, getMenuScreen, getNavigation, type NavigationItem, type Permission } from "../config/access";
import { currentUser } from "../mocks/member";
import { Button } from "@/components/ui/button";
import { Sidebar, SidebarContent, SidebarGroup, SidebarGroupContent, SidebarHeader, SidebarMenu, SidebarMenuButton, SidebarMenuItem, SidebarMenuSub, SidebarMenuSubItem, SidebarMenuSubButton, SidebarProvider, useSidebar } from "@/components/ui/sidebar";
import { HomePage } from "../features/member/pages/HomePage";
import { AccountPage } from "../features/member/pages/AccountPage";
import { LessonDetailPage, LessonsPage } from "../features/member/pages/LessonsPage";
import { dashboardPreview, dashboardPreviewTime } from "@/features/dashboard/preview";
import { getDashboardSnapshot, roleLabels, type DashboardUser } from "@/features/dashboard/model";
import { PassDetailPage, PassesPage } from "../features/member/pages/PassesPage";
import { reservationDialog, ReservationsPage } from "../features/member/pages/ReservationsPage";
import { NotificationsPage } from "../features/member/pages/NotificationsPage";
import type { DialogState, Screen } from "./routes";
import { DashboardError, DashboardLoading } from "@/features/dashboard/DashboardState";
import type { HomeView } from "@/features/dashboard/viewState";
import { AdminWorkspace, useAdminDashboard } from "@/features/admin/AdminWorkspace";
import { QueryState } from "@/features/admin/AdminUI";
import { TimetablePage } from "@/features/timetable/TimetablePage";
import "./legacy.css";
import "./shell.css";

function App({ user, name = user.name, onLogout, onLogin, initialHomeView = "normal" }: { user: DashboardUser; name?: string; onLogout: (trigger?: HTMLElement) => void; onLogin: () => void; initialHomeView?: HomeView }) {
  const [homeView, setHomeView] = useState(initialHomeView);
  const [screen, setScreen] = useState<Screen>("home");
  const [selectedId, setSelectedId] = useState(101);
  const [data, setData] = useState(() => initialHomeView === "empty" ? { lessons: [], reservations: [], absences: [], notifications: [] } : dashboardPreview);
  const admin = useAdminDashboard(user.role === "ADMIN");
  const viewData = user.role === "ADMIN" && homeView !== "empty" ? admin.data : data;
  const snapshot = getDashboardSnapshot(viewData, user, dashboardPreviewTime);
  const unreadCount = user.role === "ADMIN" ? admin.unread : snapshot.unread;
  const navigation = getNavigation(user.role, currentUser.permissions);
  const onReadNotification = (id: number) => setData(previous => ({ ...previous, notifications: previous.notifications.map(item => item.id === id && item.recipientId === user.id ? { ...item, read: true } : item) }));
  const [dialog, setDialog] = useState<DialogState>(null);

  const go = (next: Screen, id?: number) => {
    if (user.role === "ADMIN") {
      if (next === "today-lessons") next = "admin-lessons";
      if (next === "attendance-overview") next = "admin-lessons";
      if (next === "absence-requests") next = "admin-absences";
    }
    if (!canAccessScreen(next, user.role, currentUser.permissions)) return;
    if (user.role === "MEMBER" && next === "reservation-detail" && id !== undefined) { setDialog(reservationDialog(id)); return; }
    if (id !== undefined) setSelectedId(id);
    else if (next === "admin-settlement-create") setSelectedId(0);
    setScreen(next);
    window.scrollTo(0, 0);
  };

  const can = (permission: Permission) => user.role === "MEMBER" && currentUser.permissions.has(permission);

  return (
    <SidebarProvider className="member-sidebar-shell" style={{ "--sidebar-width": "var(--navigation-width)" } as CSSProperties}>
      <AppSidebar user={user} navigation={navigation} screen={screen} onNavigate={go} />
      <main className={`main-content${screen === "home" ? ` home-main${user.role === "MEMBER" ? " member-home-main" : ""}` : ""}`}>
        <AppHeader
          location={<><MobileMenuButton /><PageBreadcrumb screen={screen} role={user.role} /></>}
          name={name}
          unreadCount={unreadCount}
          onNotificationClick={() => go("notifications")}
          onLogout={onLogout}
        />
        <div className="content">
          {screen === "notifications" ? <NotificationsPage role={user.role} onNavigate={go} /> : screen === "timetable" ? <TimetablePage user={user} onNavigate={go} canReserve={can("reservation.create")} /> : screen === "home" && homeView === "loading" ? <DashboardLoading /> : screen === "home" && ["error", "expired", "forbidden"].includes(homeView) ? <DashboardError status={homeView === "expired" ? 401 : homeView === "forbidden" ? 403 : 500} onRetry={() => setHomeView("normal")} onLogin={onLogin} /> : user.role === "ADMIN" ? screen === "home" ? <QueryState query={admin}><HomePage user={user} data={viewData} screen="home" empty={homeView === "empty"} onNavigate={go} onReadNotification={admin.read} /></QueryState> : <AdminWorkspace key={`${screen}-${selectedId}`} screen={screen} id={selectedId} onNavigate={go} /> : screen === "home" || user.role !== "MEMBER" ? <HomePage user={user} data={data} screen={screen} empty={homeView === "empty"} onNavigate={go} onReadNotification={onReadNotification} /> : <MemberScreen
            screen={screen}
            selectedId={selectedId}
            onNavigate={go}
            onDialog={setDialog}
            canReserve={can("reservation.create")}
            canWaitlist={can("waitlist.create")}
            canCancel={can("reservation.cancel")}
            canUpdate={can("profile.update")}
            canChangePassword={can("password.change")}
          />}
        </div>
        <AppFooter />
      </main>
      {dialog && <Dialog dialog={dialog} onClose={() => setDialog(null)} />}
    </SidebarProvider>
  );
}

function AppSidebar({ user, navigation, screen, onNavigate }: { user: DashboardUser; navigation: NavigationItem[]; screen: Screen; onNavigate: (screen: Screen) => void }) {
  const { isMobile, setOpenMobile } = useSidebar();
  const [openMenu, setOpenMenu] = useState<string | null>(getMenuScreen(screen));
  useEffect(() => {
    setOpenMenu(getMenuScreen(screen));
  }, [screen]);
  const navigate = (target: Screen) => { onNavigate(target); if (isMobile) setOpenMobile(false); };
  return (
    <Sidebar className="member-sidebar" aria-label={`${roleLabels[user.role]} 사이드 메뉴`}>
      <SidebarHeader className="member-sidebar-header">
        <button type="button" className="member-sidebar-brand" aria-label="홈으로 이동" onClick={() => { onNavigate("home"); if (isMobile) setOpenMobile(false); }}>
          <BrandLogo variant="sidebar" />
        </button>
        {isMobile && <Button variant="cancel" onClick={() => setOpenMobile(false)}>메뉴 닫기</Button>}
      </SidebarHeader>
      <SidebarContent>
        <SidebarGroup><SidebarGroupContent><nav aria-label={`${roleLabels[user.role]} 메뉴`}><SidebarMenu>
        {navigation.map((item) => {
          const active = getMenuScreen(screen) === item.screen;
          if (item.children) return <Collapsible.Root key={item.screen} asChild open={openMenu === item.screen} onOpenChange={open => setOpenMenu(open ? item.screen : null)}>
            <SidebarMenuItem>
              <Collapsible.Trigger asChild><SidebarMenuButton variant="brand" size="lg" isActive={active}>
                <span>{item.label}</span><ChevronRight className="navigation-disclosure" aria-hidden="true" />
              </SidebarMenuButton></Collapsible.Trigger>
              <Collapsible.Content><SidebarMenuSub className="member-subnavigation">
                {item.children.map(child => {
                  const selected = child.activeScreens.includes(screen);
                  return <SidebarMenuSubItem key={child.screen}><SidebarMenuSubButton asChild isActive={selected} className="member-subnavigation-button">
                    <button type="button" aria-current={selected ? 'page' : undefined} onClick={() => navigate(child.screen)}>{child.label}</button>
                  </SidebarMenuSubButton></SidebarMenuSubItem>;
                })}
              </SidebarMenuSub></Collapsible.Content>
            </SidebarMenuItem>
          </Collapsible.Root>;
          return <SidebarMenuItem key={item.screen}>
            <SidebarMenuButton variant="brand" size="lg" isActive={active} aria-current={active ? "page" : undefined} onClick={() => { onNavigate(item.screen); if (isMobile) setOpenMobile(false); }}>
              <span>{item.label}</span>
            </SidebarMenuButton>
          </SidebarMenuItem>;
        })}
        </SidebarMenu></nav></SidebarGroupContent></SidebarGroup>
      </SidebarContent>
    </Sidebar>
  );
}

function MobileMenuButton() {
  const { isMobile, openMobile, toggleSidebar } = useSidebar();
  if (!isMobile) return null;
  return <Button variant="outline" aria-expanded={openMobile} onClick={toggleSidebar}>메뉴 열기</Button>;
}

function MemberScreen({ screen, selectedId, onNavigate, onDialog, canReserve, canWaitlist, canCancel, canUpdate, canChangePassword }: { screen: Screen; selectedId: number; onNavigate: (screen: Screen, id?: number) => void; onDialog: (dialog: DialogState) => void; canReserve: boolean; canWaitlist: boolean; canCancel: boolean; canUpdate: boolean; canChangePassword: boolean }) {
  switch (screen) {

    case "classes": return <LessonsPage onOpen={(id) => onNavigate("lesson-detail", id)} onDialog={onDialog} canReserve={canReserve} canWaitlist={canWaitlist} />;
    case "lesson-detail": return <LessonDetailPage lessonId={selectedId} onBack={() => onNavigate("classes")} onDialog={onDialog} canReserve={canReserve} canWaitlist={canWaitlist} />;
    case "passes": return <PassesPage onOpen={(id) => onNavigate("pass-detail", id)} />;
    case "pass-detail": return <PassDetailPage passId={selectedId} onBack={() => onNavigate("passes")} />;
    case "reservations": return <ReservationsPage onDialog={onDialog} canCancel={canCancel} />;
    case "profile": return <AccountPage onDialog={onDialog} canUpdate={canUpdate} canChangePassword={canChangePassword} />;
    default: return null;
  }
}

export { App };


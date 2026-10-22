import { useEffect, useRef, useState } from "react";
import * as api from "@/api/generated/api";
import { configureSession, renewSession, setAccessToken } from "@/api/session";
import { mocksEnabled } from "@/api/runtime";
import { DashboardLoading } from "@/features/dashboard/DashboardState";
import { useQueryClient } from "@tanstack/react-query";
import { toast } from "sonner";
import { App as MemberApp } from "./app/App";
import { Toaster } from "@/components/ui/sonner";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { LoginPage } from "@/features/auth/pages/LoginPage";
import { EmailVerificationPage } from "@/features/auth/pages/EmailVerificationPage";
import { SignUpPage } from "@/features/auth/pages/SignUpPage";
import { NewPasswordPage, PasswordResetRequestPage } from "@/features/auth/pages/PasswordResetPage";
import { authPreview, type PreviewMember, type VerifiedEmail } from "@/features/auth/preview";
import type { HomeView } from "@/features/dashboard/viewState";

type Screen = "login" | "verify" | "signup" | "reset-request" | "new-password" | "member";
export function App() {
  const queryClient = useQueryClient();
  const [screen, setScreen] = useState<Screen>("login");
  const [verified, setVerified] = useState<VerifiedEmail | null>(null);
  const [resetToken, setResetToken] = useState("");
  const [loginEmail, setLoginEmail] = useState("");
  const [member, setMember] = useState<PreviewMember | null>(null);
  const [homeView, setHomeView] = useState<HomeView>("normal");
  const [logoutOpen, setLogoutOpen] = useState(false);
  const [restoring, setRestoring] = useState(!mocksEnabled);
  const login = api.useLogin(),
    refresh = api.useRefreshToken(),
    logout = api.useLogout();
  const profile = api.useGetMyProfile({ query: { enabled: false, retry: false } });
  const restoreRequest = useRef<Promise<api.Member | undefined> | null>(null);
  useEffect(
    () =>
      configureSession(
        async () => (await refresh.mutateAsync()).accessToken,
        () => {
          queryClient.clear();
          setMember(null);
          setScreen("login");
        },
      ),
    [refresh.mutateAsync, queryClient],
  );
  useEffect(() => {
    if (mocksEnabled) return;
    let active = true;
    restoreRequest.current ??= renewSession().then(
      async () => (await profile.refetch({ throwOnError: true })).data,
    );
    void restoreRequest.current
      .then((user) => {
        if (active && user) {
          setMember(user);
          setScreen("member");
        }
      })
      .catch(() => {
        setAccessToken(null);
      })
      .finally(() => {
        if (active) setRestoring(false);
      });
    return () => {
      active = false;
    };
  }, [profile.refetch]);
  const logoutOpener = useRef<HTMLElement | null>(null);
  const requestLogout = (trigger?: HTMLElement) => {
    logoutOpener.current = trigger ?? (document.activeElement as HTMLElement | null);
    setLogoutOpen(true);
  };
  const loginScreen = () => {
    setVerified(null);
    setResetToken("");
    setScreen("login");
    window.scrollTo(0, 0);
  };
  const navigate = (next: Screen) => {
    setScreen(next);
    window.scrollTo(0, 0);
  };
  const showMember = (user: PreviewMember, view: HomeView = "normal") => {
    if (mocksEnabled) authPreview.session = user;
    queryClient.clear();
    setHomeView(view);
    setMember(user);
    navigate("member");
  };
  const authenticate = async (email: string, password: string) => {
    const tokens = await login.mutateAsync({ data: { email, password } });
    setAccessToken(tokens.accessToken);
    try {
      const result = await profile.refetch({ throwOnError: true });
      if (!result.data) throw new Error("내 정보를 불러오지 못했습니다.");
      showMember(result.data);
    } catch (error) {
      setAccessToken(null);
      throw error;
    }
  };
  const endSession = () => {
    setAccessToken(null);
    authPreview.logout();
    queryClient.clear();
    setMember(null);
    loginScreen();
  };
  let page;
  if (restoring) {
    page = <DashboardLoading />;
  } else if (screen === "member" && member) {
    page = (
      <div className="member-preview">
        <MemberApp
          key={member.id}
          user={member}
          name={profile.data?.id === member.id ? profile.data.name : member.name}
          onLogout={requestLogout}
          initialHomeView={homeView}
          onLogin={endSession}
        />
      </div>
    );
  } else if (screen === "verify" || (screen === "signup" && !verified)) {
    page = (
      <EmailVerificationPage
        onLogin={loginScreen}
        onVerified={(value) => {
          setVerified(value);
          navigate("signup");
        }}
      />
    );
  } else if (screen === "signup" && verified) {
    page = (
      <SignUpPage
        verified={verified}
        onLogin={loginScreen}
        onVerify={() => {
          setVerified(null);
          navigate("verify");
        }}
        onComplete={(email) => {
          setLoginEmail(email);
          loginScreen();
        }}
      />
    );
  } else if (screen === "reset-request") {
    page = (
      <PasswordResetRequestPage
        onLogin={loginScreen}
        onReset={(token) => {
          setResetToken(token);
          navigate("new-password");
        }}
      />
    );
  } else if (screen === "new-password") {
    page = (
      <NewPasswordPage
        token={resetToken}
        onLogin={loginScreen}
        onRequest={() => navigate("reset-request")}
      />
    );
  } else {
    page = (
      <LoginPage
        initialEmail={loginEmail}
        onAuthenticate={authenticate}
        onLogin={showMember}
        onPreview={showMember}
        onVerify={() => navigate("verify")}
        onReset={() => navigate("reset-request")}
      />
    );
  }
  return (
    <>
      {page}
      <Toaster
        theme="light"
        icons={{ success: null, error: null, info: null, warning: null, loading: null }}
      />
      <AlertDialog
        open={logoutOpen}
        onOpenChange={setLogoutOpen}
      >
        <AlertDialogContent
          className="menu-dialog"
          onCloseAutoFocus={(event) => {
            if (logoutOpener.current?.isConnected) {
              event.preventDefault();
              logoutOpener.current.focus();
            }
          }}
        >
          <AlertDialogHeader>
            <AlertDialogTitle>로그아웃하시겠어요?</AlertDialogTitle>
            <AlertDialogDescription>
              로그인 화면으로 돌아갑니다. 다시 이용하려면 로그인해 주세요.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter variant="equal">
            <AlertDialogAction
              disabled={logout.isPending}
              onClick={async (event) => {
                event.preventDefault();
                try {
                  if (!mocksEnabled) await logout.mutateAsync();
                  endSession();
                  setLogoutOpen(false);
                  toast.success("로그아웃되었습니다.");
                } catch (error) {
                  toast.error(
                    error instanceof Error
                      ? error.message
                      : "로그아웃하지 못했습니다. 다시 시도해 주세요.",
                  );
                }
              }}
            >
              {logout.isPending ? "로그아웃 중…" : "로그아웃"}
            </AlertDialogAction>
            <AlertDialogCancel>취소</AlertDialogCancel>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}

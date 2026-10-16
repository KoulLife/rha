import { useState } from "react";
import { mocksEnabled } from "@/api/runtime";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { FieldGroup } from "@/components/ui/field";
import { Field, FieldLabel } from "@/components/ui/field";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { homeViewLabels, type HomeView } from "@/features/dashboard/viewState";
import { AuthField, AuthLayout } from "../components/AuthLayout";
import {
  authPreview,
  demoAccount,
  demoAccounts,
  emailRule,
  errorMessage,
  previewDelay,
  type PreviewMember,
} from "../preview";

type LoginValues = { email: string; password: string };
export function LoginPage({
  onPreview,
  onLogin,
  onAuthenticate,
  onVerify,
  onReset,
  initialEmail = "",
}: {
  onAuthenticate: (email: string, password: string) => Promise<void>;
  onPreview: (member: PreviewMember, view: HomeView) => void;
  onLogin: (member: PreviewMember) => void;
  onVerify: () => void;
  onReset: () => void;
  initialEmail?: string;
}) {
  const [error, setError] = useState("");
  const [homeView, setHomeView] = useState<HomeView>("normal");
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginValues>({
    defaultValues: { email: initialEmail, password: "" },
    mode: "onTouched",
  });
  return (
    <AuthLayout
      variant="login"
      title="로그인"
      description="R.HA 필라테스 관리 시스템"
      preview={
        mocksEnabled && (
          <>
            <details className="auth-demo-account">
              <summary>계정 정보 보기</summary>
              {demoAccounts.map((account) => (
                <p key={account.email}>
                  {account.role === "MEMBER"
                    ? "회원"
                    : account.role === "INSTRUCTOR"
                      ? "강사"
                      : "관리자"}
                  : {account.email}
                </p>
              ))}
              <p>공통 비밀번호: {demoAccount.password}</p>
            </details>
            <details className="auth-demo-account">
              <summary>화면 상태 · {homeViewLabels[homeView]}</summary>
              <Field className="auth-preview-state">
                <FieldLabel htmlFor="home-preview-state">홈 화면 상태</FieldLabel>
                <Select
                  value={homeView}
                  onValueChange={(value) => setHomeView(value as HomeView)}
                >
                  <SelectTrigger id="home-preview-state">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {Object.entries(homeViewLabels).map(([value, label]) => (
                      <SelectItem
                        key={value}
                        value={value}
                      >
                        {label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </Field>
            </details>
            <div className="auth-preview-actions">
              {demoAccounts.map((account) => (
                <Button
                  key={account.role}
                  variant="outline"
                  className="auth-preview-button"
                  onClick={() => onPreview(account, homeView)}
                >
                  {account.role === "MEMBER"
                    ? "회원"
                    : account.role === "INSTRUCTOR"
                      ? "강사"
                      : "관리자"}{" "}
                  화면 열기
                </Button>
              ))}
            </div>
          </>
        )
      }
    >
      <form
        noValidate
        onSubmit={handleSubmit(async (values) => {
          setError("");
          try {
            if (mocksEnabled) {
              await previewDelay();
              onLogin(authPreview.login(values.email.trim(), values.password));
            } else await onAuthenticate(values.email.trim(), values.password);
            toast.success("로그인되었습니다.");
          } catch (error) {
            setError(errorMessage(error));
          }
        })}
      >
        <FieldGroup className="auth-fields">
          <AuthField
            label="이메일"
            id="login-email"
            type="email"
            autoComplete="username"
            placeholder="name@example.com"
            error={errors.email?.message}
            {...register("email", emailRule)}
          />
          <AuthField
            label="비밀번호"
            id="login-password"
            type="password"
            autoComplete="current-password"
            placeholder="비밀번호를 입력하세요"
            error={errors.password?.message}
            {...register("password", { required: "비밀번호를 입력해 주세요." })}
          />
        </FieldGroup>
        <div className="auth-recovery">
          <a
            href="#password-reset"
            onClick={(event) => {
              event.preventDefault();
              onReset();
            }}
          >
            비밀번호를 잊으셨나요?
          </a>
        </div>
        {error && (
          <Alert
            variant="destructive"
            className="auth-notice"
          >
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}
        <Button
          type="submit"
          className="auth-submit"
          disabled={isSubmitting}
        >
          {isSubmitting ? "로그인 중…" : "로그인"}
        </Button>
      </form>
      <p className="auth-signup">
        계정이 없으신가요?{" "}
        <a
          href="#email-verification"
          onClick={(event) => {
            event.preventDefault();
            onVerify();
          }}
        >
          이메일 인증 후 회원가입
        </a>
      </p>
    </AuthLayout>
  );
}

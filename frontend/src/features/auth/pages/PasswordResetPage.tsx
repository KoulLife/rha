import * as api from "@/api/generated/api";
import { mocksEnabled } from "@/api/runtime";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { FieldGroup } from "@/components/ui/field";
import { AuthField, AuthLayout } from "../components/AuthLayout";
import { authPreview, emailRule, errorMessage, passwordRule, previewDelay } from "../preview";

export function PasswordResetRequestPage({ onLogin, onReset }: { onLogin: () => void; onReset: (token: string) => void }) {
  const request = api.useRequestPasswordReset();
  const [error, setError] = useState("");
  const [requested, setRequested] = useState(false);
  const [token, setToken] = useState("");
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm<{ email: string }>({ mode: "onTouched" });
  return (
    <AuthLayout title={requested ? "재설정 안내 요청 완료" : "비밀번호 재설정"} description={requested ? "이메일의 안내를 따라 비밀번호를 다시 설정하세요." : "가입한 이메일로 재설정 안내를 요청해 주세요."} preview={requested && <Button variant="outline" className="auth-preview-button" onClick={() => onReset(token)}>비밀번호 재설정 진행</Button>}>
      {requested ? <>
        <Alert><AlertDescription>가입된 이메일이라면 비밀번호 재설정 안내가 전달됩니다. 메일이 보이지 않으면 스팸함도 확인해 주세요.</AlertDescription></Alert>
        <Button className="auth-submit" onClick={onLogin}>로그인으로 돌아가기</Button>
        <Button variant="outline" className="auth-preview-button" onClick={() => setRequested(false)}>안내 다시 요청하기</Button>
      </> : <form noValidate onSubmit={handleSubmit(async ({ email }) => {
        setError("");
        try {
          if (mocksEnabled) { await previewDelay(); setToken(authPreview.requestReset(email.trim())); }
          else await request.mutateAsync({ data: { email: email.trim() } });
          setRequested(true); toast.success("재설정 안내 요청을 접수했습니다.");
        } catch (error) { setError(errorMessage(error)); }
      })}>
        <AuthField label="이메일" id="reset-email" type="email" autoComplete="email" placeholder="가입한 이메일 주소" error={errors.email?.message} {...register("email", emailRule)} />
        {error && <Alert variant="destructive"><AlertDescription>{error}</AlertDescription></Alert>}
        <Button className="auth-submit" type="submit" disabled={isSubmitting}>{isSubmitting ? "요청 중…" : "재설정 안내 요청"}</Button>
        <p className="auth-signup"><a href="#login" onClick={(event) => { event.preventDefault(); onLogin(); }}>로그인으로 돌아가기</a></p>
      </form>}
    </AuthLayout>
  );
}

export function NewPasswordPage({ token, onLogin, onRequest }: { token: string; onLogin: () => void; onRequest: () => void }) {
  const [error, setError] = useState("");
  const resetPassword = api.useResetPassword();
  const [complete, setComplete] = useState(false);
  const { register, handleSubmit, getValues, formState: { errors, isSubmitting } } = useForm<{ password: string; confirmPassword: string; token: string }>({ mode: "onTouched" });
  return (
    <AuthLayout title={complete ? "비밀번호 변경 완료" : "새 비밀번호 설정"} description={complete ? "새 비밀번호로 다시 로그인해 주세요." : "새 비밀번호를 입력해 계정을 안전하게 관리하세요."}>
      {complete ? <>
        <Alert><AlertDescription>비밀번호를 변경했습니다. 이전 비밀번호로는 로그인할 수 없습니다.</AlertDescription></Alert>
        <Button className="auth-submit" onClick={onLogin}>로그인으로 돌아가기</Button>
      </> : <>
        <form noValidate onSubmit={handleSubmit(async ({ password, token: enteredToken }) => {
          setError("");
          try {
            if (mocksEnabled) { await previewDelay(); authPreview.resetPassword(token, password); }
            else await resetPassword.mutateAsync({ data: { token: token || enteredToken.trim(), newPassword: password } });
            setComplete(true); toast.success("비밀번호가 변경되었습니다.");
          }
          catch (error) { setError(errorMessage(error)); }
        })}>
          <FieldGroup className="auth-fields">
            {!token && <AuthField label="재설정 토큰" id="reset-token" autoComplete="off" error={errors.token?.message} {...register("token", { required: "안내받은 재설정 토큰을 입력해 주세요." })} />}
            <AuthField label="새 비밀번호" id="new-password" type="password" autoComplete="new-password" placeholder="8~64자 비밀번호" description="8~64자로 입력해 주세요." error={errors.password?.message} {...register("password", passwordRule)} />
            <AuthField label="새 비밀번호 확인" id="new-password-confirm" type="password" autoComplete="new-password" placeholder="비밀번호를 다시 입력하세요" error={errors.confirmPassword?.message} {...register("confirmPassword", { required: "비밀번호를 다시 입력해 주세요.", validate: (value) => value === getValues("password") || "비밀번호가 일치하지 않습니다." })} />
          </FieldGroup>
          {error && <Alert variant="destructive" className="auth-notice"><AlertDescription>{error}</AlertDescription></Alert>}
          <Button type="submit" className="auth-submit" disabled={isSubmitting}>{isSubmitting ? "변경 중…" : "비밀번호 변경"}</Button>
        </form>
        <div className="auth-actions"><Button variant="ghost" onClick={onRequest}>재설정 안내 다시 요청</Button><Button variant="ghost" onClick={onLogin}>로그인으로</Button></div>
      </>}
    </AuthLayout>
  );
}

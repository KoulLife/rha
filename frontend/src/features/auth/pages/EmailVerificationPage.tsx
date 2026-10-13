import { useState } from "react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { FieldGroup } from "@/components/ui/field";
import { AuthField, AuthLayout } from "../components/AuthLayout";
import { authPreview, emailRule, errorMessage, previewDelay, type VerifiedEmail } from "../preview";

export function EmailVerificationPage({ onLogin, onVerified }: { onLogin: () => void; onVerified: (value: VerifiedEmail) => void }) {
  const [sentEmail, setSentEmail] = useState("");
  const [demoCode, setDemoCode] = useState("");
  const [error, setError] = useState("");
  const [resending, setResending] = useState(false);
  const { register, handleSubmit, resetField, formState: { errors, isSubmitting } } = useForm<{ email: string; code: string }>({ mode: "onTouched", shouldUnregister: true });
  const send = async (email: string) => {
    await previewDelay();
    setDemoCode(authPreview.requestVerification(email));
    setSentEmail(email);
    resetField("code");
    toast.success("인증 코드를 요청했습니다.");
  };
  return (
    <AuthLayout title="이메일 인증" description="회원가입을 위해 이메일을 먼저 확인해 주세요." preview={demoCode && <p className="auth-demo-code">미리보기 인증 코드: <strong>{demoCode}</strong> · 유효 시간 5분</p>}>
      <p className="auth-step">회원가입 1 / 2 · 이메일 확인</p>
      <form noValidate onSubmit={handleSubmit(async ({ email, code }) => {
        setError("");
        try {
          if (!sentEmail) await send(email.trim());
          else { await previewDelay(); onVerified(authPreview.confirmVerification(sentEmail, code)); toast.success("이메일 인증이 완료되었습니다."); }
        } catch (error) { setError(errorMessage(error)); }
      })}>
        <FieldGroup className="auth-fields">
          <AuthField label="이메일" id="verify-email" type="email" autoComplete="email" placeholder="가입할 이메일 주소" readOnly={Boolean(sentEmail)} error={errors.email?.message} {...register("email", emailRule)} />
          {sentEmail && <>
            <Alert><AlertDescription>{sentEmail}의 인증 코드를 입력해 주세요.</AlertDescription></Alert>
            <AuthField label="인증 코드" id="verify-code" inputMode="numeric" autoComplete="one-time-code" maxLength={6} placeholder="6자리 숫자" error={errors.code?.message} {...register("code", { required: "인증 코드를 입력해 주세요.", pattern: { value: /^\d{6}$/, message: "6자리 숫자를 입력해 주세요." } })} />
          </>}
        </FieldGroup>
        {error && <Alert variant="destructive" className="auth-notice"><AlertDescription>{error}</AlertDescription></Alert>}
        <Button type="submit" className="auth-submit" disabled={isSubmitting || resending}>{isSubmitting ? "처리 중…" : sentEmail ? "인증 코드 확인" : "인증 코드 받기"}</Button>
      </form>
      {sentEmail && <div className="auth-actions">
        <Button variant="outline" disabled={isSubmitting || resending} onClick={async () => {
          setResending(true); setError("");
          try { await send(sentEmail); } catch (error) { setError(errorMessage(error)); }
          finally { setResending(false); }
        }}>{resending ? "다시 요청 중…" : "인증 코드 다시 받기"}</Button>
        <Button variant="ghost" disabled={isSubmitting || resending} onClick={() => { setSentEmail(""); setDemoCode(""); setError(""); resetField("code"); }}>이메일 변경</Button>
      </div>}
      <p className="auth-signup"><a href="#login" onClick={(event) => { event.preventDefault(); onLogin(); }}>로그인으로 돌아가기</a></p>
    </AuthLayout>
  );
}

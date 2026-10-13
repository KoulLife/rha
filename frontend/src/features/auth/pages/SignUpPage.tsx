import { useState } from "react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { FieldGroup } from "@/components/ui/field";
import { AuthField, AuthLayout } from "../components/AuthLayout";
import { authPreview, errorMessage, passwordRule, previewDelay, type VerifiedEmail } from "../preview";

type Values = { name: string; phoneNumber: string; birthDate: string; address: string; password: string; confirmPassword: string };
export function SignUpPage({ verified, onComplete, onVerify, onLogin }: {
  verified: VerifiedEmail; onComplete: (email: string) => void; onVerify: () => void; onLogin: () => void;
}) {
  const [error, setError] = useState("");
  const { register, handleSubmit, getValues, formState: { errors, isSubmitting } } = useForm<Values>({ mode: "onTouched" });
  return (
    <AuthLayout title="회원가입" description="기본 정보를 입력하고 R.HA와 함께 시작하세요.">
      <p className="auth-step">회원가입 2 / 2 · 회원 정보 입력</p>
      <form noValidate onSubmit={handleSubmit(async (values) => {
        setError(""); await previewDelay();
        try {
          authPreview.signUp({ ...values, name: values.name.trim(), phoneNumber: values.phoneNumber.replace(/-/g, ""), email: verified.email, emailVerificationToken: verified.token });
          toast.success("회원가입이 완료되었습니다. 로그인해 주세요."); onComplete(verified.email);
        } catch (error) { setError(errorMessage(error)); }
      })}>
        <FieldGroup className="auth-fields">
          <AuthField label="인증된 이메일" id="signup-email" type="email" value={verified.email} readOnly description="회원 계정으로 가입됩니다." />
          <div className="auth-form-row">
            <AuthField label="이름" id="signup-name" autoComplete="name" placeholder="이름" maxLength={50} error={errors.name?.message} {...register("name", { required: "이름을 입력해 주세요.", validate: (value) => Boolean(value.trim()) || "이름을 입력해 주세요.", maxLength: { value: 50, message: "이름은 50자 이내로 입력해 주세요." } })} />
            <AuthField label="생년월일" id="signup-birth" type="date" autoComplete="bday" error={errors.birthDate?.message} {...register("birthDate", { required: "생년월일을 입력해 주세요." })} />
          </div>
          <AuthField label="휴대전화" id="signup-phone" type="tel" autoComplete="tel" placeholder="010-1234-5678" error={errors.phoneNumber?.message} {...register("phoneNumber", { required: "휴대전화 번호를 입력해 주세요.", validate: (value) => /^01\d{8,9}$/.test(value.replace(/-/g, "")) || "올바른 휴대전화 번호를 입력해 주세요." })} />
          <AuthField label="주소 (선택)" id="signup-address" autoComplete="street-address" placeholder="주소를 입력하세요" maxLength={200} error={errors.address?.message} {...register("address", { maxLength: { value: 200, message: "주소는 200자 이내로 입력해 주세요." } })} />
          <AuthField label="비밀번호" id="signup-password" type="password" autoComplete="new-password" placeholder="8~64자 비밀번호" description="8~64자로 입력해 주세요." error={errors.password?.message} {...register("password", passwordRule)} />
          <AuthField label="비밀번호 확인" id="signup-confirm" type="password" autoComplete="new-password" placeholder="비밀번호를 다시 입력하세요" error={errors.confirmPassword?.message} {...register("confirmPassword", { required: "비밀번호를 다시 입력해 주세요.", validate: (value) => value === getValues("password") || "비밀번호가 일치하지 않습니다." })} />
        </FieldGroup>
        {error && <Alert variant="destructive" className="auth-notice"><AlertDescription>{error}</AlertDescription></Alert>}
        <Button type="submit" className="auth-submit" disabled={isSubmitting}>{isSubmitting ? "가입 중…" : "회원가입"}</Button>
      </form>
      <div className="auth-actions"><Button variant="ghost" onClick={onVerify}>이메일 다시 인증</Button><Button variant="ghost" onClick={onLogin}>로그인으로</Button></div>
    </AuthLayout>
  );
}

// 화면 검토용 메모리 데이터. API, 실제 메일, 브라우저 저장소를 사용하지 않는다.
export const demoAccount = { email: "demo@rha.example", password: "RhaDemo123!", name: "홍길동" };
export const demoAccounts = [
  { ...demoAccount, id: 1, role: "MEMBER" as const },
  {
    email: "instructor@rha.example",
    password: demoAccount.password,
    name: "김강사",
    id: 2,
    role: "INSTRUCTOR" as const,
  },
  {
    email: "admin@rha.example",
    password: demoAccount.password,
    name: "이관리",
    id: 3,
    role: "ADMIN" as const,
  },
];
export type PreviewMember = {
  email: string;
  name: string;
  role: "MEMBER" | "INSTRUCTOR" | "ADMIN";
  id: number;
};
export type Registration = {
  email: string;
  password: string;
  name: string;
  phoneNumber: string;
  birthDate: string;
  address?: string;
  emailVerificationToken: string;
};
export type VerifiedEmail = { email: string; token: string };

export class AuthPreview {
  private accounts = new Map<
    string,
    { password: string; name: string; role: PreviewMember["role"]; id: number }
  >(demoAccounts.map((account) => [account.email, account]));
  private codes = new Map<string, { code: string; expiresAt: number }>();
  private verified = new Map<string, { email: string; expiresAt: number }>();
  private resets = new Map<string, { email: string; expiresAt: number }>();
  private sequence = 0;
  session: PreviewMember | null = null;
  private now: () => number;

  constructor(now: () => number = Date.now) {
    this.now = now;
  }

  requestVerification(email: string) {
    if (this.accounts.has(email))
      throw new Error("이미 가입된 이메일입니다. 로그인하거나 비밀번호를 재설정해 주세요.");
    const code = String(123456 + this.sequence++)
      .padStart(6, "0")
      .slice(-6);
    this.codes.set(email, { code, expiresAt: this.now() + 5 * 60_000 });
    for (const [token, value] of this.verified)
      if (value.email === email) this.verified.delete(token);
    return code;
  }

  confirmVerification(email: string, code: string): VerifiedEmail {
    const pending = this.codes.get(email);
    if (!pending || pending.code !== code || pending.expiresAt <= this.now()) {
      throw new Error(
        "인증 코드가 일치하지 않거나 만료됐습니다. 확인하거나 새 코드를 요청해 주세요.",
      );
    }
    this.codes.delete(email);
    const token = `preview-verification-${++this.sequence}`;
    this.verified.set(token, { email, expiresAt: this.now() + 30 * 60_000 });
    return { email, token };
  }

  signUp(input: Registration) {
    const verification = this.verified.get(input.emailVerificationToken);
    if (
      !verification ||
      verification.email !== input.email ||
      verification.expiresAt <= this.now()
    ) {
      throw new Error("이메일 인증이 만료됐습니다. 이메일 인증부터 다시 진행해 주세요.");
    }
    if (this.accounts.has(input.email)) throw new Error("이미 가입된 이메일입니다.");
    if (input.password.length < 8 || input.password.length > 64)
      throw new Error("비밀번호는 8~64자로 입력해 주세요.");
    this.accounts.set(input.email, {
      name: input.name,
      password: input.password,
      role: "MEMBER",
      id: ++this.sequence + 100,
    });
    this.verified.delete(input.emailVerificationToken);
  }

  login(email: string, password: string): PreviewMember {
    const account = this.accounts.get(email);
    if (!account || account.password !== password)
      throw new Error("이메일 또는 비밀번호가 올바르지 않습니다.");
    this.session = { email, name: account.name, role: account.role, id: account.id };
    return this.session;
  }

  requestReset(email: string) {
    for (const [token, value] of this.resets) if (value.email === email) this.resets.delete(token);
    // 가입 여부와 관계없이 같은 접수 화면/미리보기 링크를 제공한다.
    const token = `preview-reset-${++this.sequence}`;
    this.resets.set(token, { email, expiresAt: this.now() + 30 * 60_000 });
    return token;
  }

  resetPassword(token: string, password: string) {
    const request = this.resets.get(token);
    if (!request || request.expiresAt <= this.now() || !this.accounts.has(request.email)) {
      throw new Error(
        "재설정 링크가 유효하지 않거나 만료됐습니다. 안내 메일을 다시 요청해 주세요.",
      );
    }
    if (password.length < 8 || password.length > 64)
      throw new Error("비밀번호는 8~64자로 입력해 주세요.");
    const account = this.accounts.get(request.email)!;
    this.accounts.set(request.email, { ...account, password });
    for (const [key, value] of this.resets)
      if (value.email === request.email) this.resets.delete(key);
    this.session = null;
  }

  logout() {
    this.session = null;
  }
}

export const authPreview = new AuthPreview();
export const emailRule = {
  required: "이메일을 입력해 주세요.",
  pattern: { value: /^[^\s@]+@[^\s@]+\.[^\s@]+$/, message: "올바른 이메일 주소를 입력해 주세요." },
};
export const passwordRule = {
  required: "비밀번호를 입력해 주세요.",
  minLength: { value: 8, message: "비밀번호는 8자 이상 입력해 주세요." },
  maxLength: { value: 64, message: "비밀번호는 64자 이내로 입력해 주세요." },
};
export const errorMessage = (error: unknown) =>
  error instanceof Error ? error.message : "처리하지 못했습니다. 다시 시도해 주세요.";
export const previewDelay = () => new Promise<void>((resolve) => setTimeout(resolve, 180));

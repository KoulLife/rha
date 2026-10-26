# 메일 발송 (2026-10-26)

## 결정

- 인증 코드·비밀번호 재설정·계정 초대 메일은 **SMTP**로 보낸다 (`backend/.../auth/SmtpMailer.java`).
- 지금은 도메인이 없으므로 **Gmail SMTP**를 쓴다. 도메인을 구입하면 **AWS SES SMTP**로 옮긴다.
- 발송 서비스는 환경변수(`MAIL_*`)로만 정한다. 전환할 때 코드는 바꾸지 않는다.

| 프로필 | 메일 구현 | 비고 |
| --- | --- | --- |
| `local` (기본값) | `LoggingEmailVerificationMailer`, `LoggingPasswordResetMailer` | 코드·토큰을 서버 로그에만 남긴다 |
| `prod` (배포 서버) | `SmtpMailer` | `MAIL_*` 중 하나라도 없으면 기동에 실패한다 |

배포 서버는 `deploy/compose.yaml`에서 `SPRING_PROFILES_ACTIVE=prod`로 실행한다. 이 설정이 없던 동안에는 배포 서버도 `local`로 동작해 메일이 발송되지 않았다.

## 설정 값 (AWS Parameter Store)

`scripts/deploy-app.sh`가 배포 시 아래 값을 읽어 백엔드 컨테이너에 넘긴다. **값이 없으면 백엔드·프론트 배포 모두 1단계에서 중단된다.**

| 파라미터 | 형식 | Gmail SMTP 값 |
| --- | --- | --- |
| `/rha/dev/mail/host` | String | `smtp.gmail.com` |
| `/rha/dev/mail/username` | String | 발송용 Gmail 주소 |
| `/rha/dev/mail/password` | SecureString | Gmail 앱 비밀번호 (16자리, 공백 제외) |
| `/rha/dev/mail/from` | String | 발송용 Gmail 주소 (Gmail은 로그인 계정과 다른 주소를 쓰면 로그인 계정으로 바꿔 보낸다) |

포트는 587(STARTTLS)로 고정한다. 바꿔야 하면 `MAIL_PORT`를 추가한다.

### Gmail 준비

1. 발송 전용 Gmail 계정을 만든다 (개인 계정 사용 금지).
2. Google 계정 → 보안 → 2단계 인증을 켠다.
3. Google 계정 → 보안 → 앱 비밀번호에서 비밀번호를 발급한다.
4. 위 표대로 Parameter Store에 등록한다.

```bash
aws ssm put-parameter --region ap-northeast-2 --name /rha/dev/mail/host     --type String       --value smtp.gmail.com
aws ssm put-parameter --region ap-northeast-2 --name /rha/dev/mail/username --type String       --value <gmail 주소>
aws ssm put-parameter --region ap-northeast-2 --name /rha/dev/mail/password --type SecureString --value <앱 비밀번호>
aws ssm put-parameter --region ap-northeast-2 --name /rha/dev/mail/from     --type String       --value <gmail 주소>
```

Gmail 개인 계정의 발송 한도는 하루 약 500통이다 (현재 예상 사용량 100통 미만).

## SES로 전환 (도메인 구입 후)

1. SES(ap-northeast-2)에 도메인을 등록하고 DKIM DNS 레코드를 추가해 인증한다.
2. SES 샌드박스 해제(프로덕션 액세스)를 신청한다. 승인 전에는 인증한 주소로만 보낼 수 있다.
3. SES 콘솔 → SMTP 설정에서 SMTP 자격 증명을 발급한다 (AWS 액세스 키와 다른 값).
4. Parameter Store 값을 바꾸고 백엔드 배포 워크플로를 수동 실행(`workflow_dispatch`)한다.
   (같은 이미지로 재실행하면 배포 스크립트가 건너뛸 수 있으므로, 건너뛰면 컨테이너를 재생성한다.)

| 파라미터 | SES 값 |
| --- | --- |
| `/rha/dev/mail/host` | `email-smtp.ap-northeast-2.amazonaws.com` |
| `/rha/dev/mail/username` | SES SMTP 사용자 이름 |
| `/rha/dev/mail/password` | SES SMTP 비밀번호 |
| `/rha/dev/mail/from` | `no-reply@<도메인>` |

## 알려진 차이

- OpenAPI는 비밀번호 재설정·초대 메일에 "링크(토큰 포함)"를 보낸다고 설명하지만, 현재 프론트는 URL에서 토큰을 읽지 않고 화면에서 토큰을 직접 입력받는다. 그래서 메일에는 링크 대신 토큰을 적는다. 링크로 바꾸려면 프론트 라우팅과 서비스 주소(도메인)가 필요하다.

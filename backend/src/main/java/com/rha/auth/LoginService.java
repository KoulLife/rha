package com.rha.auth;

import com.rha.common.error.ApiException;
import com.rha.common.error.ErrorCode;
import com.rha.member.Member;
import com.rha.member.MemberRepository;
import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.rha.activity.ActivityAction;
import com.rha.activity.ActivityChange;
import com.rha.activity.ActivityLogRecorder;
import com.rha.activity.Masking;

@Service
public class LoginService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenIssuer tokenIssuer;
    /** 가입되지 않은 이메일도 같은 비용으로 비밀번호를 비교해, 응답 시간으로 가입 여부가 드러나지 않게 한다. */
    private final String dummyPasswordHash;
    private final ActivityLogRecorder activityLogRecorder;

    public LoginService(MemberRepository memberRepository,
            PasswordEncoder passwordEncoder,
            AuthTokenIssuer tokenIssuer,
            ActivityLogRecorder activityLogRecorder) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenIssuer = tokenIssuer;
        this.activityLogRecorder = activityLogRecorder;
        this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    @Transactional
    public AuthTokens login(String email, String password) {
        Instant now = Instant.now();
        Member member = memberRepository.findByEmail(email).orElse(null);

        // 비밀번호를 먼저 검증한다. 자격 증명을 증명하지 못한 요청에는 계정 상태를 알려주지 않는다.
        // 초대만 받고 비밀번호를 설정하지 않은 계정(passwordHash 없음)도 같은 응답이다.
        String storedHash = member != null && member.getPasswordHash() != null
                ? member.getPasswordHash()
                : dummyPasswordHash;
        boolean passwordMatches = passwordEncoder.matches(password, storedHash);
        if (member == null || member.getPasswordHash() == null || !passwordMatches) {
            recordFailure(member, email);
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!member.isActive()) {
            recordFailure(member, email);
            throw new ApiException(ErrorCode.ACCOUNT_DISABLED);
        }

        AuthTokens tokens = tokenIssuer.issue(member, now);
        activityLogRecorder.record(ActivityAction.LOGIN, member.getId(), member.getId(), "MEMBER", member.getId(),
                "로그인");
        return tokens;
    }

    /** 요청이 거절되어 이 트랜잭션이 롤백되어도 남도록 별도 트랜잭션으로 기록한다. 비밀번호는 남기지 않는다. */
    private void recordFailure(Member member, String email) {
        Long memberId = member == null ? null : member.getId();
        activityLogRecorder.recordIndependently(ActivityAction.LOGIN_FAILED, memberId, memberId, null, null,
                "로그인 실패 (이메일 " + Masking.email(email) + ")");
    }
}

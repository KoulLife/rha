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
public class PasswordChangeService {

    private final MemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final ActivityLogRecorder activityLogRecorder;

    public PasswordChangeService(MemberRepository memberRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            ActivityLogRecorder activityLogRecorder) {
        this.memberRepository = memberRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.activityLogRecorder = activityLogRecorder;
    }

    /** 비밀번호를 변경하고, 그 회원의 모든 Refresh Token을 폐기한다. */
    @Transactional
    public void change(Long memberId, String currentPassword, String newPassword) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
        if (!member.isActive()) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED);
        }

        // 현재 비밀번호를 먼저 검증한다. 틀리면 새 비밀번호가 현재와 같은지는 알려주지 않는다.
        String currentHash = member.getPasswordHash();
        if (currentHash == null || !passwordEncoder.matches(currentPassword, currentHash)) {
            throw new ApiException(ErrorCode.INVALID_CURRENT_PASSWORD);
        }
        if (passwordEncoder.matches(newPassword, currentHash)) {
            throw new ApiException(ErrorCode.SAME_AS_CURRENT_PASSWORD);
        }

        Instant now = Instant.now();
        member.changePassword(passwordEncoder.encode(newPassword), now);
        refreshTokenRepository.revokeAllOf(memberId, now);
        activityLogRecorder.record(ActivityAction.PASSWORD_CHANGED, memberId, memberId, "MEMBER", memberId,
                "비밀번호 변경");
    }
}

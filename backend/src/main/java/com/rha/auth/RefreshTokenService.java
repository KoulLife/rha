package com.rha.auth;

import com.rha.common.error.ApiException;
import com.rha.common.error.ErrorCode;
import com.rha.member.Member;
import com.rha.member.MemberRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.rha.activity.ActivityAction;
import com.rha.activity.ActivityChange;
import com.rha.activity.ActivityLogRecorder;
import com.rha.activity.Masking;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final MemberRepository memberRepository;
    private final AuthTokenIssuer tokenIssuer;
    private final ActivityLogRecorder activityLogRecorder;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
            MemberRepository memberRepository,
            AuthTokenIssuer tokenIssuer,
            ActivityLogRecorder activityLogRecorder) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.memberRepository = memberRepository;
        this.tokenIssuer = tokenIssuer;
        this.activityLogRecorder = activityLogRecorder;
    }

    /** Refresh Token을 회전한다. 사용한 토큰은 폐기되고, 새 Access Token과 Refresh Token이 발급된다. */
    @Transactional
    public AuthTokens refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new ApiException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        Instant now = Instant.now();
        String tokenHash = TokenHasher.sha256(rawRefreshToken);

        RefreshToken token = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REFRESH_TOKEN));

        // 만료·폐기 여부 확인과 폐기를 한 번의 UPDATE로 처리한다. 같은 토큰으로 동시에 요청해도 하나만 성공한다.
        if (refreshTokenRepository.revoke(tokenHash, now) != 1) {
            throw new ApiException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        Member member = memberRepository.findById(token.getMemberId())
                .filter(Member::isActive)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REFRESH_TOKEN));

        return tokenIssuer.issue(member, now);
    }

    /**
     * 로그아웃하는 회원 본인의 Refresh Token을 폐기한다. 다른 기기의 토큰은 그대로 둔다.
     * 토큰이 없거나 알 수 없거나 다른 회원의 것이거나 이미 폐기됐어도 오류 없이 넘어간다(멱등).
     */
    @Transactional
    public void revoke(Long memberId, String rawRefreshToken) {
        if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
            refreshTokenRepository.revokeOwned(TokenHasher.sha256(rawRefreshToken), memberId, Instant.now());
        }
        activityLogRecorder.record(ActivityAction.LOGOUT, memberId, memberId, "MEMBER", memberId, "로그아웃");
    }
}

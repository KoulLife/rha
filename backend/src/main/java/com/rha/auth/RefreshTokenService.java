package com.rha.auth;

import com.rha.common.error.ApiException;
import com.rha.common.error.ErrorCode;
import com.rha.member.Member;
import com.rha.member.MemberRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final MemberRepository memberRepository;
    private final AuthTokenIssuer tokenIssuer;

    public RefreshTokenService(RefreshTokenRepository refreshTokenRepository,
            MemberRepository memberRepository,
            AuthTokenIssuer tokenIssuer) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.memberRepository = memberRepository;
        this.tokenIssuer = tokenIssuer;
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
}

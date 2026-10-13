package com.rha.auth;

import com.rha.member.Member;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 회원에게 Access Token과 새 Refresh Token을 발급한다. Refresh Token은 해시만 저장한다. */
@Component
public class AuthTokenIssuer {

    private final RefreshTokenRepository refreshTokenRepository;
    private final AccessTokenProvider accessTokenProvider;
    private final Duration refreshTokenTtl;
    private final SecureRandom random = new SecureRandom();

    public AuthTokenIssuer(RefreshTokenRepository refreshTokenRepository,
            AccessTokenProvider accessTokenProvider,
            @Value("${rha.auth.refresh-token-ttl:14d}") Duration refreshTokenTtl) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.accessTokenProvider = accessTokenProvider;
        this.refreshTokenTtl = refreshTokenTtl;
    }

    public AuthTokens issue(Member member, Instant now) {
        String refreshToken = newRandomToken();
        refreshTokenRepository.save(new RefreshToken(
                member.getId(), TokenHasher.sha256(refreshToken), now.plus(refreshTokenTtl), now));

        TokenResponse tokens = new TokenResponse(
                accessTokenProvider.issue(member, now), accessTokenProvider.expiresInSeconds());
        return new AuthTokens(tokens, refreshToken, refreshTokenTtl);
    }

    private String newRandomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

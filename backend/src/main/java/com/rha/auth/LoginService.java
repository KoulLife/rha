package com.rha.auth;

import com.rha.common.error.ApiException;
import com.rha.common.error.ErrorCode;
import com.rha.member.Member;
import com.rha.member.MemberRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LoginService {

    private final MemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenProvider accessTokenProvider;
    private final Duration refreshTokenTtl;
    private final SecureRandom random = new SecureRandom();
    /** 가입되지 않은 이메일도 같은 비용으로 비밀번호를 비교해, 응답 시간으로 가입 여부가 드러나지 않게 한다. */
    private final String dummyPasswordHash;

    public LoginService(MemberRepository memberRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            AccessTokenProvider accessTokenProvider,
            @Value("${rha.auth.refresh-token-ttl:14d}") Duration refreshTokenTtl) {
        this.memberRepository = memberRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenProvider = accessTokenProvider;
        this.refreshTokenTtl = refreshTokenTtl;
        this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    @Transactional
    public LoginResult login(String email, String password) {
        Instant now = Instant.now();
        Member member = memberRepository.findByEmail(email).orElse(null);

        // 비밀번호를 먼저 검증한다. 자격 증명을 증명하지 못한 요청에는 계정 상태를 알려주지 않는다.
        // 초대만 받고 비밀번호를 설정하지 않은 계정(passwordHash 없음)도 같은 응답이다.
        String storedHash = member != null && member.getPasswordHash() != null
                ? member.getPasswordHash()
                : dummyPasswordHash;
        boolean passwordMatches = passwordEncoder.matches(password, storedHash);
        if (member == null || member.getPasswordHash() == null || !passwordMatches) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }
        if (!member.isActive()) {
            throw new ApiException(ErrorCode.ACCOUNT_DISABLED);
        }

        String refreshToken = newRandomToken();
        refreshTokenRepository.save(new RefreshToken(
                member.getId(), TokenHasher.sha256(refreshToken), now.plus(refreshTokenTtl), now));

        TokenResponse tokens = new TokenResponse(
                accessTokenProvider.issue(member, now), accessTokenProvider.expiresInSeconds());
        return new LoginResult(tokens, refreshToken, refreshTokenTtl);
    }

    private String newRandomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

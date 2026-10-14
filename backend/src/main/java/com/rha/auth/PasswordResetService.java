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
public class PasswordResetService {

    private static final String TOKEN_PREFIX = "prt_";

    private final MemberRepository memberRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordResetMailer mailer;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final Duration tokenTtl;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(MemberRepository memberRepository,
            PasswordResetTokenRepository tokenRepository,
            PasswordResetMailer mailer,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            @Value("${rha.auth.password-reset.token-ttl:30m}") Duration tokenTtl) {
        this.memberRepository = memberRepository;
        this.tokenRepository = tokenRepository;
        this.mailer = mailer;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenTtl = tokenTtl;
    }

    /**
     * 가입 여부를 응답으로 알 수 없도록, 대상이 없거나 ACTIVE가 아니면 아무 일도 하지 않고 조용히 끝난다.
     */
    @Transactional
    public void request(String email) {
        Member member = memberRepository.findByEmail(email).filter(Member::isActive).orElse(null);
        if (member == null) {
            return;
        }

        tokenRepository.deleteUnusedByMemberId(member.getId());

        String token = TOKEN_PREFIX + newRandomToken();
        Instant now = Instant.now();
        tokenRepository.save(
                new PasswordResetToken(member.getId(), TokenHasher.sha256(token), now.plus(tokenTtl), now));

        mailer.sendResetToken(member.getEmail(), token);
    }

    /**
     * 토큰을 사용 처리하고 비밀번호를 바꾼다. 토큰이 없거나, 이미 사용했거나, 만료되었거나, 계정이 ACTIVE가 아니면
     * 모두 INVALID_RESET_TOKEN이며 이때는 토큰 사용 처리도 롤백된다.
     */
    @Transactional
    public void reset(String token, String newPassword) {
        String tokenHash = TokenHasher.sha256(token);
        Instant now = Instant.now();

        PasswordResetToken resetToken = tokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_RESET_TOKEN));
        // 조회와 사용 처리 사이에 같은 토큰이 동시에 쓰일 수 있으므로, 사용 처리는 조건부 UPDATE 한 번으로 한다.
        if (tokenRepository.markUsed(tokenHash, now) == 0) {
            throw new ApiException(ErrorCode.INVALID_RESET_TOKEN);
        }

        Member member = memberRepository.findById(resetToken.getMemberId())
                .filter(Member::isActive)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_RESET_TOKEN));

        member.changePassword(passwordEncoder.encode(newPassword), now);
        refreshTokenRepository.revokeAllOf(member.getId(), now);
        tokenRepository.deleteUnusedByMemberId(member.getId());
    }

    private String newRandomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

package com.rha.auth;

import com.rha.member.Member;
import com.rha.member.MemberRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PasswordResetService {

    private static final String TOKEN_PREFIX = "prt_";

    private final MemberRepository memberRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordResetMailer mailer;
    private final Duration tokenTtl;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(MemberRepository memberRepository,
            PasswordResetTokenRepository tokenRepository,
            PasswordResetMailer mailer,
            @Value("${rha.auth.password-reset.token-ttl:30m}") Duration tokenTtl) {
        this.memberRepository = memberRepository;
        this.tokenRepository = tokenRepository;
        this.mailer = mailer;
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

    private String newRandomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

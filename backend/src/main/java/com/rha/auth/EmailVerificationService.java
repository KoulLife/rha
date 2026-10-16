package com.rha.auth;

import com.rha.common.error.ApiException;
import com.rha.common.error.ErrorCode;
import com.rha.common.persistence.RowLock;
import com.rha.member.MemberRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailVerificationService {

    private static final String TOKEN_PREFIX = "evt_";

    private final MemberRepository memberRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final EmailVerificationMailer mailer;
    private final RowLock rowLock;
    private final Duration codeTtl;
    private final Duration tokenTtl;
    private final SecureRandom random = new SecureRandom();

    public EmailVerificationService(MemberRepository memberRepository,
            EmailVerificationRepository emailVerificationRepository,
            EmailVerificationMailer mailer, RowLock rowLock,
            @Value("${rha.auth.email-verification.code-ttl:5m}") Duration codeTtl,
            @Value("${rha.auth.email-verification.token-ttl:30m}") Duration tokenTtl) {
        this.memberRepository = memberRepository;
        this.emailVerificationRepository = emailVerificationRepository;
        this.mailer = mailer;
        this.rowLock = rowLock;
        this.codeTtl = codeTtl;
        this.tokenTtl = tokenTtl;
    }

    @Transactional
    public void requestVerification(String email) {
        if (memberRepository.existsByEmail(email)) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        emailVerificationRepository.deleteAllByEmail(email);

        String code = "%06d".formatted(random.nextInt(1_000_000));
        Instant now = Instant.now();
        emailVerificationRepository.save(
                new EmailVerification(email, TokenHasher.sha256(code), now.plus(codeTtl), now));

        mailer.sendCode(email, code);
    }

    /** 코드가 틀려도 실패 횟수는 남겨야 하므로 거절 응답에서 롤백하지 않는다. */
    @Transactional(noRollbackFor = ApiException.class)
    public EmailVerificationToken confirm(String email, String code) {
        Instant now = Instant.now();
        EmailVerification verification = emailVerificationRepository.findFirstByEmailOrderByIdDesc(email)
                .map(rowLock::lock)
                .filter(v -> v.canAttempt(now))
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_VERIFICATION_CODE));
        if (!verification.matchesCode(TokenHasher.sha256(code))) {
            verification.recordFailure();
            throw new ApiException(ErrorCode.INVALID_VERIFICATION_CODE);
        }

        String token = TOKEN_PREFIX + newRandomToken();
        verification.verify(TokenHasher.sha256(token), now, now.plus(tokenTtl));

        return new EmailVerificationToken(token, tokenTtl.toSeconds());
    }

    private String newRandomToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

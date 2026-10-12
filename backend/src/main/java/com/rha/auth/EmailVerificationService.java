package com.rha.auth;

import com.rha.common.error.ApiException;
import com.rha.common.error.ErrorCode;
import com.rha.member.MemberRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailVerificationService {

    private final MemberRepository memberRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final EmailVerificationMailer mailer;
    private final Duration codeTtl;
    private final SecureRandom random = new SecureRandom();

    public EmailVerificationService(MemberRepository memberRepository,
            EmailVerificationRepository emailVerificationRepository,
            EmailVerificationMailer mailer,
            @Value("${rha.auth.email-verification.code-ttl:5m}") Duration codeTtl) {
        this.memberRepository = memberRepository;
        this.emailVerificationRepository = emailVerificationRepository;
        this.mailer = mailer;
        this.codeTtl = codeTtl;
    }

    @Transactional
    public void requestVerification(String email) {
        if (memberRepository.existsByEmail(email)) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        emailVerificationRepository.deleteAllByEmail(email);

        String code = "%06d".formatted(random.nextInt(1_000_000));
        Instant now = Instant.now();
        emailVerificationRepository.save(new EmailVerification(email, sha256(code), now.plus(codeTtl), now));

        mailer.sendCode(email, code);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

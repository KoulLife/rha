package com.rha.auth;

import com.rha.common.error.ApiException;
import com.rha.common.error.ErrorCode;
import com.rha.member.Member;
import com.rha.member.MemberRepository;
import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SignUpService {

    private final MemberRepository memberRepository;
    private final EmailVerificationRepository emailVerificationRepository;
    private final PasswordEncoder passwordEncoder;

    public SignUpService(MemberRepository memberRepository,
            EmailVerificationRepository emailVerificationRepository,
            PasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.emailVerificationRepository = emailVerificationRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Member signUp(SignUpRequest request) {
        Instant now = Instant.now();

        // 토큰 검증을 먼저 한다. 이메일 소유를 증명하지 못한 요청에는 가입 여부를 알려주지 않는다.
        EmailVerification verification = emailVerificationRepository
                .findByVerificationTokenHash(TokenHasher.sha256(request.emailVerificationToken()))
                .filter(v -> v.matchesToken(request.email(), now))
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_VERIFICATION_TOKEN));

        if (memberRepository.existsByEmail(request.email())) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        verification.markUsed(now);
        return memberRepository.save(Member.signUp(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.name(),
                request.phoneNumber(),
                request.birthDate(),
                request.address(),
                now));
    }
}

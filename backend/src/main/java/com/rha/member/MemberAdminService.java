package com.rha.member;

import com.rha.auth.PasswordResetService;
import com.rha.common.error.ApiException;
import com.rha.common.error.ErrorCode;
import java.time.Instant;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberAdminService {

    private final MemberRepository memberRepository;
    private final PasswordResetService passwordResetService;

    public MemberAdminService(MemberRepository memberRepository, PasswordResetService passwordResetService) {
        this.memberRepository = memberRepository;
        this.passwordResetService = passwordResetService;
    }

    /**
     * 강사 또는 관리자 계정을 비밀번호 없이 등록하고 초대 메일을 보낸다.
     * 메일 발송이 실패하면 트랜잭션이 롤백되어 계정도 만들어지지 않는다.
     */
    @Transactional
    public Member createMember(Long callerId, MemberCreateRequest request) {
        requireAdmin(callerId);

        if (memberRepository.existsByEmail(request.email())) {
            throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        Member member = Member.invite(request.email(), request.name(), request.phoneNumber(),
                request.birthDate(), request.address(), MemberRole.valueOf(request.role()), Instant.now());
        try {
            memberRepository.saveAndFlush(member);
        } catch (DataIntegrityViolationException e) {
            // existsByEmail 이후 같은 이메일이 동시에 등록된 경우
            throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        passwordResetService.invite(member);
        return member;
    }

    /** 토큰의 역할이 아니라 DB의 현재 역할·상태로 확인한다. 역할이 바뀐 직후의 오래된 토큰을 막기 위함이다. */
    private void requireAdmin(Long callerId) {
        Member caller = memberRepository.findById(callerId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
        if (!caller.isRole(MemberRole.ADMIN) || !caller.isActive()) {
            throw new ApiException(ErrorCode.PERMISSION_DENIED);
        }
    }
}

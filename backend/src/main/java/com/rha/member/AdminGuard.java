package com.rha.member;

import com.rha.common.error.ApiException;
import com.rha.common.error.ErrorCode;
import org.springframework.stereotype.Component;

/** 관리자 전용 API의 호출자 확인. */
@Component
public class AdminGuard {

    private final MemberRepository memberRepository;

    public AdminGuard(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    /** 토큰의 역할이 아니라 DB의 현재 역할·상태로 확인한다. 역할이 바뀐 직후의 오래된 토큰을 막기 위함이다. */
    public void requireAdmin(Long callerId) {
        Member caller = memberRepository.findById(callerId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
        if (!caller.isRole(MemberRole.ADMIN) || !caller.isActive()) {
            throw new ApiException(ErrorCode.PERMISSION_DENIED);
        }
    }
}

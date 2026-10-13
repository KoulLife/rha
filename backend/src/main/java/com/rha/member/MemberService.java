package com.rha.member;

import com.rha.common.error.ApiException;
import com.rha.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberService {

    private final MemberRepository memberRepository;

    public MemberService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Transactional(readOnly = true)
    public Member getMyProfile(Long memberId) {
        // 토큰은 유효하지만 회원이 존재하지 않으면 인증 정보가 유효하지 않은 것으로 본다.
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
    }
}

package com.rha.auth;

import com.rha.common.error.ApiException;
import com.rha.common.error.ErrorCode;
import com.rha.member.Member;
import com.rha.member.MemberRepository;
import com.rha.member.MemberRole;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountWithdrawalService {

    private final MemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    public AccountWithdrawalService(MemberRepository memberRepository,
            RefreshTokenRepository refreshTokenRepository) {
        this.memberRepository = memberRepository;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    /**
     * MEMBER 역할의 회원을 탈퇴 처리하고 모든 Refresh Token을 폐기한다.
     * 시작 전 예약 확인과 이용권 무효화는 예약·이용권 기능이 구현될 때 추가한다.
     */
    @Transactional
    public void withdraw(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
        if (!member.isRole(MemberRole.MEMBER)) {
            throw new ApiException(ErrorCode.FORBIDDEN_ROLE);
        }

        Instant now = Instant.now();
        member.withdraw(now);
        refreshTokenRepository.revokeAllOf(memberId, now);
    }
}

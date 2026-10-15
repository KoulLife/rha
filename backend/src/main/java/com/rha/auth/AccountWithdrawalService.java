package com.rha.auth;

import com.rha.common.error.ApiException;
import com.rha.common.error.ErrorCode;
import com.rha.member.Member;
import com.rha.member.MemberRepository;
import com.rha.member.MemberRole;
import com.rha.reservation.ReservationRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountWithdrawalService {

    private final MemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ReservationRepository reservationRepository;

    public AccountWithdrawalService(MemberRepository memberRepository,
            RefreshTokenRepository refreshTokenRepository, ReservationRepository reservationRepository) {
        this.reservationRepository = reservationRepository;
        this.memberRepository = memberRepository;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    /**
     * MEMBER 역할의 회원을 탈퇴 처리하고 모든 Refresh Token을 폐기한다. 시작 전 예약이 있으면 거절한다.
     * 남은 이용권은 회원이 활성 상태가 아니므로 예약에 쓸 수 없게 된다.
     */
    @Transactional
    public void withdraw(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
        if (!member.isRole(MemberRole.MEMBER)) {
            throw new ApiException(ErrorCode.FORBIDDEN_ROLE);
        }

        Instant now = Instant.now();
        if (reservationRepository.existsUpcomingOf(memberId, now)) {
            throw new ApiException(ErrorCode.MEMBER_HAS_UPCOMING_RESERVATIONS);
        }
        member.withdraw(now);
        refreshTokenRepository.revokeAllOf(memberId, now);
    }
}

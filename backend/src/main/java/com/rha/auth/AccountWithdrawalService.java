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
import com.rha.activity.ActivityAction;
import com.rha.activity.ActivityChange;
import com.rha.activity.ActivityLogRecorder;
import com.rha.activity.Josa;
import com.rha.activity.Masking;

@Service
public class AccountWithdrawalService {

    private final MemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final ReservationRepository reservationRepository;
    private final ActivityLogRecorder activityLogRecorder;

    public AccountWithdrawalService(MemberRepository memberRepository,
            RefreshTokenRepository refreshTokenRepository, ReservationRepository reservationRepository,
            ActivityLogRecorder activityLogRecorder) {
        this.activityLogRecorder = activityLogRecorder;
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
        // 탈퇴 처리가 이름을 바꾸더라도 요약에는 탈퇴 전 이름이 남도록 먼저 기록한다.
        activityLogRecorder.record(ActivityAction.ACCOUNT_WITHDRAWN, memberId, memberId, "MEMBER", memberId,
                "회원탈퇴했다.");
        member.withdraw(now);
        refreshTokenRepository.revokeAllOf(memberId, now);
    }
}

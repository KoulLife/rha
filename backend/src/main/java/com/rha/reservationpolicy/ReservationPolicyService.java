package com.rha.reservationpolicy;

import com.rha.common.validation.RequestValidator;
import com.rha.member.AdminGuard;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationPolicyService {

    private final ReservationPolicyRepository policyRepository;
    private final AdminGuard adminGuard;
    private final RequestValidator requestValidator;

    public ReservationPolicyService(ReservationPolicyRepository policyRepository, AdminGuard adminGuard,
            RequestValidator requestValidator) {
        this.policyRepository = policyRepository;
        this.adminGuard = adminGuard;
        this.requestValidator = requestValidator;
    }

    @Transactional(readOnly = true)
    public ReservationPolicyResponse getReservationPolicy() {
        return ReservationPolicyResponse.from(policy());
    }

    /** 정책 값을 교체한다. 관리자 확인(403) 뒤에 입력(400)을 검증한다. */
    @Transactional
    public ReservationPolicyResponse updateReservationPolicy(Long callerId, ReservationPolicyRequest request) {
        adminGuard.requireAdmin(callerId);
        requestValidator.validate(request);

        ReservationPolicy policy = policy();
        policy.replace(request.cancelDeadlineHours(), request.allowUnpaidReservation(), Instant.now());
        return ReservationPolicyResponse.from(policy);
    }

    /** 마이그레이션이 만든 단일 행. */
    private ReservationPolicy policy() {
        return policyRepository.findById(ReservationPolicy.SINGLETON_ID).orElseThrow();
    }
}

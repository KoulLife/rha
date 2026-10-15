package com.rha.reservationpolicy;

import com.rha.auth.AuthenticatedMember;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 쓰기 API의 본문 검증은 관리자 여부를 확인한 뒤에 하므로 `@Valid`를 붙이지 않는다. */
@RestController
@RequestMapping("/reservation-policy")
public class ReservationPolicyController {

    private final ReservationPolicyService policyService;

    public ReservationPolicyController(ReservationPolicyService policyService) {
        this.policyService = policyService;
    }

    @GetMapping
    public ReservationPolicyResponse getReservationPolicy() {
        return policyService.getReservationPolicy();
    }

    @PutMapping
    public ReservationPolicyResponse updateReservationPolicy(@AuthenticationPrincipal AuthenticatedMember me,
            @RequestBody ReservationPolicyRequest request) {
        return policyService.updateReservationPolicy(me.id(), request);
    }
}

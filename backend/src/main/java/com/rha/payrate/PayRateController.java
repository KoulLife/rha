package com.rha.payrate;

import com.rha.auth.AuthenticatedMember;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 쓰기 API의 본문 검증은 관리자 여부를 확인한 뒤에 하므로 `@Valid`를 붙이지 않는다. */
@RestController
@RequestMapping("/members/{memberId}/pay-rates")
public class PayRateController {

    private final PayRateService payRateService;

    public PayRateController(PayRateService payRateService) {
        this.payRateService = payRateService;
    }

    @GetMapping
    public PayRatesResponse getPayRates(@AuthenticationPrincipal AuthenticatedMember me,
            @PathVariable Long memberId) {
        return payRateService.getPayRates(me.id(), memberId);
    }

    @PutMapping
    public PayRatesResponse updatePayRates(@AuthenticationPrincipal AuthenticatedMember me,
            @PathVariable Long memberId, @RequestBody PayRatesRequest request) {
        return payRateService.updatePayRates(me.id(), memberId, request);
    }
}

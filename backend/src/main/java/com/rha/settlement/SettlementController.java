package com.rha.settlement;

import com.rha.auth.AuthenticatedMember;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 쓰기 API의 본문 검증은 관리자 여부를 확인한 뒤에 하므로 `@Valid`를 붙이지 않는다. */
@RestController
@RequestMapping("/settlements")
public class SettlementController {

    private final SettlementService settlementService;

    public SettlementController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    @GetMapping
    public SettlementPageResponse getSettlements(@AuthenticationPrincipal AuthenticatedMember me,
            @RequestParam(required = false) Long instructorId,
            @RequestParam(required = false) SettlementStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodTo,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return settlementService.getSettlements(me.id(), instructorId, status, periodFrom, periodTo, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SettlementResponse createSettlement(@AuthenticationPrincipal AuthenticatedMember me,
            @RequestBody SettlementCreateRequest request) {
        return settlementService.createSettlement(me.id(), request);
    }

    @GetMapping("/{settlementId}")
    public SettlementResponse getSettlement(@AuthenticationPrincipal AuthenticatedMember me,
            @PathVariable Long settlementId) {
        return settlementService.getSettlement(me.id(), settlementId);
    }

    @DeleteMapping("/{settlementId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void discardSettlement(@AuthenticationPrincipal AuthenticatedMember me,
            @PathVariable Long settlementId) {
        settlementService.discard(me.id(), settlementId);
    }

    @PostMapping("/{settlementId}/review-requests")
    public SettlementResponse requestSettlementReview(@AuthenticationPrincipal AuthenticatedMember me,
            @PathVariable Long settlementId) {
        return settlementService.requestReview(me.id(), settlementId);
    }

    @PostMapping("/{settlementId}/confirmations")
    public SettlementResponse confirmSettlement(@AuthenticationPrincipal AuthenticatedMember me,
            @PathVariable Long settlementId) {
        return settlementService.confirm(me.id(), settlementId);
    }

    @PostMapping("/{settlementId}/payments")
    public SettlementResponse recordSettlementPayment(@AuthenticationPrincipal AuthenticatedMember me,
            @PathVariable Long settlementId, @RequestBody SettlementPaymentRequest request) {
        return settlementService.recordPayment(me.id(), settlementId, request);
    }
}

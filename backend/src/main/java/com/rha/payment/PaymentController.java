package com.rha.payment;

import com.rha.auth.AuthenticatedMember;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 쓰기 API의 본문 검증은 관리자 여부를 확인한 뒤에 하므로 `@Valid`를 붙이지 않는다. */
@RestController
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/passes/{passId}/payments")
    public PassPaymentPageResponse getPassPayments(@AuthenticationPrincipal AuthenticatedMember me,
            @PathVariable Long passId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return paymentService.getPassPayments(me.id(), passId, page, size);
    }

    @PostMapping("/passes/{passId}/payments")
    @ResponseStatus(HttpStatus.CREATED)
    public PassPaymentResponse recordPassPayment(@AuthenticationPrincipal AuthenticatedMember me,
            @PathVariable Long passId, @RequestBody PassPaymentCreateRequest request) {
        return paymentService.recordPayment(me.id(), passId, request);
    }

    @PatchMapping("/passes/{passId}/payments/{paymentId}")
    public PassPaymentResponse updatePassPayment(@AuthenticationPrincipal AuthenticatedMember me,
            @PathVariable Long passId, @PathVariable Long paymentId,
            @RequestBody PassPaymentUpdateRequest request) {
        return paymentService.correctPayment(me.id(), passId, paymentId, request);
    }

    @DeleteMapping("/passes/{passId}/payments/{paymentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePassPayment(@AuthenticationPrincipal AuthenticatedMember me, @PathVariable Long passId,
            @PathVariable Long paymentId) {
        paymentService.deletePayment(me.id(), passId, paymentId);
    }

    @PutMapping("/passes/{passId}/payments/{paymentId}/refund")
    public PassPaymentResponse setPassPaymentRefund(@AuthenticationPrincipal AuthenticatedMember me,
            @PathVariable Long passId, @PathVariable Long paymentId,
            @RequestBody PassPaymentRefundRequest request) {
        return paymentService.setRefund(me.id(), passId, paymentId, request);
    }

    @GetMapping("/payments")
    public PassPaymentPageResponse getPayments(@AuthenticationPrincipal AuthenticatedMember me,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate paidFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate paidTo,
            @RequestParam(required = false) PaymentMethod method,
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) @Size(max = 50) String keyword,
            @RequestParam(required = false) Boolean refunded,
            @RequestParam(defaultValue = "paidAt") @Pattern(regexp = "paidAt|amount") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction order,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return paymentService.getPayments(me.id(), new PaymentService.Filter(paidFrom, paidTo, method, memberId,
                keyword, refunded), sortBy, order, page, size);
    }
}

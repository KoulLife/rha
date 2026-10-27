package com.rha.pass;

import com.rha.member.Member;
import com.rha.passproduct.PassProduct;
import java.time.Instant;
import java.time.LocalDate;

public record PassResponse(Long id, ProductSummary passProduct, PersonSummary member, PersonSummary instructor,
        long price, long paidAmount, long unpaidAmount, PaymentStatus paymentStatus, long refundedAmount,
        LocalDate refundedAt, String refundMemo, int totalCount, int remainingCount, LocalDate startDate,
        LocalDate endDate, PassStatus status, Instant createdAt) {

    public record ProductSummary(Long id, String name) {
    }

    public record PersonSummary(Long id, String name) {
    }

    /**
     * {@code paid}는 기록된 결제 합계이다. 환불로 체크된 기록도 포함하며 환불 금액은 빼지 않는다.
     * 환불된 이용권은 더 받을 금액이 없으므로 미수금 0, 결제 상태 PAID이다.
     */
    static PassResponse of(Pass pass, PassProduct product, Member member, Member instructor, long paid) {
        long unpaid = pass.isRefunded() ? 0 : pass.getPrice() - paid;
        PaymentStatus paymentStatus = pass.isRefunded() ? PaymentStatus.PAID : PaymentStatus.of(pass.getPrice(), paid);
        return new PassResponse(pass.getId(), new ProductSummary(product.getId(), product.getName()),
                new PersonSummary(member.getId(), member.getName()),
                new PersonSummary(instructor.getId(), instructor.getName()), pass.getPrice(), paid, unpaid,
                paymentStatus, pass.getRefundedAmount(), pass.getRefundedAt(), pass.getRefundMemo(),
                pass.getTotalCount(), pass.getRemainingCount(), pass.getStartDate(), pass.getEndDate(),
                pass.getStatus(), pass.getCreatedAt());
    }
}

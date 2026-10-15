package com.rha.payment;

import com.rha.member.Member;
import com.rha.pass.Pass;
import com.rha.passproduct.PassProduct;
import java.time.Instant;
import java.time.LocalDate;

public record PassPaymentResponse(Long id, PaymentMethod method, long amount, LocalDate paidAt, String memo,
        boolean refunded, LocalDate refundedAt, String refundMemo, PersonSummary member, PassSummary pass,
        PersonSummary recordedBy, Instant createdAt) {

    public record PersonSummary(Long id, String name) {
    }

    public record ProductSummary(Long id, String name) {
    }

    public record PassSummary(Long id, ProductSummary passProduct, LocalDate endDate) {
    }

    static PassPaymentResponse of(PassPayment payment, Pass pass, PassProduct product, Member member,
            Member recordedBy) {
        return new PassPaymentResponse(payment.getId(), payment.getMethod(), payment.getAmount(),
                payment.getPaidAt(), payment.getMemo(), payment.isRefunded(), payment.getRefundedAt(),
                payment.getRefundMemo(), new PersonSummary(member.getId(), member.getName()),
                new PassSummary(pass.getId(), new ProductSummary(product.getId(), product.getName()),
                        pass.getEndDate()),
                new PersonSummary(recordedBy.getId(), recordedBy.getName()), payment.getCreatedAt());
    }
}

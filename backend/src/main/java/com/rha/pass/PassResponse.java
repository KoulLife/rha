package com.rha.pass;

import com.rha.member.Member;
import com.rha.passproduct.PassProduct;
import java.time.Instant;
import java.time.LocalDate;

public record PassResponse(Long id, ProductSummary passProduct, PersonSummary member, PersonSummary instructor,
        long price, long paidAmount, long unpaidAmount, PaymentStatus paymentStatus, int totalCount,
        int remainingCount, LocalDate startDate, LocalDate endDate, PassStatus status, Instant createdAt) {

    public record ProductSummary(Long id, String name) {
    }

    public record PersonSummary(Long id, String name) {
    }

    /** 결제 기록 기능이 구현되기 전이라 결제 합계는 0이다. 금액이 0이면 완납으로 본다. */
    static PassResponse of(Pass pass, PassProduct product, Member member, Member instructor) {
        long paid = 0;
        PaymentStatus paymentStatus = pass.getPrice() == 0 ? PaymentStatus.PAID : PaymentStatus.UNPAID;
        return new PassResponse(pass.getId(), new ProductSummary(product.getId(), product.getName()),
                new PersonSummary(member.getId(), member.getName()),
                new PersonSummary(instructor.getId(), instructor.getName()), pass.getPrice(), paid,
                pass.getPrice() - paid, paymentStatus, pass.getTotalCount(), pass.getRemainingCount(),
                pass.getStartDate(), pass.getEndDate(), pass.getStatus(), pass.getCreatedAt());
    }
}

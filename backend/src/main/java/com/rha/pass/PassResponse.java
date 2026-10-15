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

    /** {@code paid}는 기록된 결제 합계이다. 환불로 체크된 기록도 포함한다. */
    static PassResponse of(Pass pass, PassProduct product, Member member, Member instructor, long paid) {
        PaymentStatus paymentStatus = PaymentStatus.of(pass.getPrice(), paid);
        return new PassResponse(pass.getId(), new ProductSummary(product.getId(), product.getName()),
                new PersonSummary(member.getId(), member.getName()),
                new PersonSummary(instructor.getId(), instructor.getName()), pass.getPrice(), paid,
                pass.getPrice() - paid, paymentStatus, pass.getTotalCount(), pass.getRemainingCount(),
                pass.getStartDate(), pass.getEndDate(), pass.getStatus(), pass.getCreatedAt());
    }
}

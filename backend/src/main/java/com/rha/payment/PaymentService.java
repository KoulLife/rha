package com.rha.payment;

import com.rha.common.error.ApiException;
import com.rha.common.error.ErrorCode;
import com.rha.common.paging.LikePattern;
import com.rha.common.paging.PageInfo;
import com.rha.common.validation.RequestValidator;
import com.rha.member.AdminGuard;
import com.rha.member.Member;
import com.rha.member.MemberRepository;
import com.rha.pass.Pass;
import com.rha.pass.PassRepository;
import com.rha.passproduct.PassProduct;
import com.rha.passproduct.PassProductRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.rha.activity.ActivityAction;
import com.rha.activity.ActivityChange;
import com.rha.activity.ActivityLogRecorder;
import com.rha.activity.ActivityTime;

/**
 * 이용권 결제 기록(관리자 전용). 이용권을 잠가 같은 이용권의 결제가 동시에 기록·정정되어도 합계가 금액을
 * 넘지 않게 한다. 기록·정정·삭제·환불 체크는 활동 이력에 남긴다.
 */
@Service
public class PaymentService {

    private static final ZoneId CENTER_ZONE = ZoneId.of("Asia/Seoul");

    /** 전체 결제 기록 조회 조건. */
    public record Filter(LocalDate paidFrom, LocalDate paidTo, PaymentMethod method, Long memberId, String keyword,
            Boolean refunded) {
    }

    private final PassPaymentRepository paymentRepository;
    private final PassRepository passRepository;
    private final PassProductRepository productRepository;
    private final MemberRepository memberRepository;
    private final AdminGuard adminGuard;
    private final RequestValidator requestValidator;
    private final ActivityLogRecorder activityLogRecorder;

    public PaymentService(PassPaymentRepository paymentRepository, PassRepository passRepository,
            PassProductRepository productRepository, MemberRepository memberRepository, AdminGuard adminGuard,
            RequestValidator requestValidator, ActivityLogRecorder activityLogRecorder) {
        this.activityLogRecorder = activityLogRecorder;
        this.paymentRepository = paymentRepository;
        this.passRepository = passRepository;
        this.productRepository = productRepository;
        this.memberRepository = memberRepository;
        this.adminGuard = adminGuard;
        this.requestValidator = requestValidator;
    }

    // ===== 조회 =====

    /** 결제일 내림차순(같으면 id 내림차순). {@code page}는 1부터 시작한다. */
    @Transactional(readOnly = true)
    public PassPaymentPageResponse getPassPayments(Long callerId, Long passId, int page, int size) {
        adminGuard.requireAdmin(callerId);
        if (!passRepository.existsById(passId)) {
            throw new ApiException(ErrorCode.NOT_FOUND);
        }
        PageRequest pageable = PageRequest.of(page - 1, size,
                Sort.by(Sort.Order.desc("paidAt"), Sort.Order.desc("id")));
        Page<PassPayment> result = paymentRepository.findByPassId(passId, pageable);
        return new PassPaymentPageResponse(toResponses(result.getContent()), PageInfo.of(result));
    }

    @Transactional(readOnly = true)
    public PassPaymentPageResponse getPayments(Long callerId, Filter filter, String sortBy, Sort.Direction order,
            int page, int size) {
        adminGuard.requireAdmin(callerId);
        if (filter.paidFrom() != null && filter.paidTo() != null && filter.paidTo().isBefore(filter.paidFrom())) {
            throw new ApiException(ErrorCode.INVALID_INPUT);
        }
        PageRequest pageable = PageRequest.of(page - 1, size,
                Sort.by(new Sort.Order(order, sortBy), new Sort.Order(order, "id")));
        Page<PassPayment> result = paymentRepository.findAll(specificationOf(filter), pageable);
        return new PassPaymentPageResponse(toResponses(result.getContent()), PageInfo.of(result));
    }

    private Specification<PassPayment> specificationOf(Filter filter) {
        String pattern = LikePattern.contains(filter.keyword());
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.paidFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<LocalDate>get("paidAt"), filter.paidFrom()));
            }
            if (filter.paidTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<LocalDate>get("paidAt"), filter.paidTo()));
            }
            if (filter.method() != null) {
                predicates.add(cb.equal(root.get("method"), filter.method()));
            }
            if (filter.refunded() != null) {
                predicates.add(cb.equal(root.get("refunded"), filter.refunded()));
            }
            if (filter.memberId() != null) {
                Subquery<Long> passes = query.subquery(Long.class);
                Root<Pass> pass = passes.from(Pass.class);
                passes.select(pass.get("id")).where(cb.equal(pass.get("memberId"), filter.memberId()));
                predicates.add(root.get("passId").in(passes));
            }
            if (pattern != null) {
                Subquery<Long> members = query.subquery(Long.class);
                Root<Member> member = members.from(Member.class);
                members.select(member.get("id")).where(cb.like(cb.lower(member.get("name")), pattern, '\\'));
                Subquery<Long> passes = query.subquery(Long.class);
                Root<Pass> pass = passes.from(Pass.class);
                passes.select(pass.get("id")).where(pass.get("memberId").in(members));
                predicates.add(root.get("passId").in(passes));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    // ===== 기록·정정·삭제·환불 =====

    /** 확인 순서: 관리자(403) → 입력(400) → 이용권(404) → 이용권 환불(409) → 합계 초과(409). */
    @Transactional
    public PassPaymentResponse recordPayment(Long callerId, Long passId, PassPaymentCreateRequest request) {
        adminGuard.requireAdmin(callerId);
        requestValidator.validate(request);
        LocalDate paidAt = parseDate(request.paidAt());
        requireNotFuture(paidAt);

        Pass pass = lockPass(passId);
        requireNotRefunded(pass);
        if (paymentRepository.sumByPassId(passId) + request.amount() > pass.getPrice()) {
            throw new ApiException(ErrorCode.PAYMENT_EXCEEDS_PRICE);
        }
        PassPayment payment = paymentRepository.save(PassPayment.record(passId,
                PaymentMethod.valueOf(request.method()), request.amount(), paidAt, request.memo(), callerId,
                Instant.now()));
        activityLogRecorder.record(ActivityAction.PAYMENT_RECORDED, callerId, pass.getMemberId(), "PASS_PAYMENT",
                payment.getId(), "이용권 #" + passId + "에 결제 " + won(payment.getAmount()) + "("
                        + payment.getMethod() + ") 기록");
        return toResponses(List.of(payment)).get(0);
    }

    /** 확인 순서: 관리자(403) → 입력(400) → 이용권·기록(404) → 이용권 환불(409) → 환불 체크(409) → 합계 초과(409). */
    @Transactional
    public PassPaymentResponse correctPayment(Long callerId, Long passId, Long paymentId,
            PassPaymentUpdateRequest request) {
        adminGuard.requireAdmin(callerId);
        requestValidator.validate(request);
        if (!request.hasAnyField()) {
            throw new ApiException(ErrorCode.INVALID_INPUT);
        }
        LocalDate paidAt = request.getPaidAt() == null ? null : parseDate(request.getPaidAt());
        if (paidAt != null) {
            requireNotFuture(paidAt);
        }

        Pass pass = lockPass(passId);
        PassPayment payment = findPayment(passId, paymentId);
        requireNotRefunded(pass);
        if (payment.isRefunded()) {
            throw new ApiException(ErrorCode.PAYMENT_REFUNDED);
        }
        if (request.getAmount() != null
                && paymentRepository.sumByPassIdExcluding(passId, paymentId) + request.getAmount()
                        > pass.getPrice()) {
            throw new ApiException(ErrorCode.PAYMENT_EXCEEDS_PRICE);
        }

        PaymentMethod method = request.getMethod() == null ? null : PaymentMethod.valueOf(request.getMethod());
        PaymentMethod beforeMethod = payment.getMethod();
        long beforeAmount = payment.getAmount();
        LocalDate beforePaidAt = payment.getPaidAt();
        String beforeMemo = payment.getMemo();
        payment.correct(method, request.getAmount(), paidAt, request.isMemoSpecified(), request.getMemo(),
                Instant.now());
        activityLogRecorder.record(ActivityAction.PAYMENT_UPDATED, callerId, pass.getMemberId(), "PASS_PAYMENT",
                payment.getId(), "이용권 #" + passId + "의 결제 기록 #" + payment.getId() + " 정정",
                ActivityChange.ifChanged("method", beforeMethod, payment.getMethod()),
                ActivityChange.ifChanged("amount", beforeAmount, payment.getAmount()),
                ActivityChange.ifChanged("paidAt", beforePaidAt, payment.getPaidAt()),
                ActivityChange.ifChanged("memo", beforeMemo, payment.getMemo()));
        return toResponses(List.of(payment)).get(0);
    }

    /** 확인 순서: 관리자(403) → 이용권·기록(404) → 이용권 환불(409) → 환불 체크(409). */
    @Transactional
    public void deletePayment(Long callerId, Long passId, Long paymentId) {
        adminGuard.requireAdmin(callerId);
        Pass pass = lockPass(passId);
        PassPayment payment = findPayment(passId, paymentId);
        requireNotRefunded(pass);
        if (payment.isRefunded()) {
            throw new ApiException(ErrorCode.PAYMENT_REFUNDED);
        }
        paymentRepository.delete(payment);
        activityLogRecorder.record(ActivityAction.PAYMENT_DELETED, callerId, pass.getMemberId(), "PASS_PAYMENT",
                payment.getId(), "이용권 #" + passId + "의 결제 " + won(payment.getAmount()) + "("
                        + payment.getMethod() + ") 기록 삭제",
                ActivityChange.of("amount", payment.getAmount(), null));
    }

    /**
     * 환불 여부를 체크하거나 해제한다. 이용권, 예약, 미수금은 바뀌지 않는다.
     * 확인 순서: 관리자(403) → 입력(400) → 이용권·기록(404) → 환불일 범위(400).
     */
    @Transactional
    public PassPaymentResponse setRefund(Long callerId, Long passId, Long paymentId,
            PassPaymentRefundRequest request) {
        adminGuard.requireAdmin(callerId);
        requestValidator.validate(request);
        LocalDate requestedDate = request.refundedAt() == null ? null : parseDate(request.refundedAt());

        Pass pass = lockPass(passId);
        PassPayment payment = findPayment(passId, paymentId);
        Instant now = Instant.now();
        boolean beforeRefunded = payment.isRefunded();
        LocalDate beforeRefundedAt = payment.getRefundedAt();
        String beforeRefundMemo = payment.getRefundMemo();
        if (!request.refunded()) {
            payment.clearRefund(now);
        } else {
            LocalDate refundedAt = requestedDate != null ? requestedDate
                    : payment.isRefunded() ? payment.getRefundedAt() : LocalDate.now(CENTER_ZONE);
            String memo = request.memo() != null ? request.memo()
                    : payment.isRefunded() ? payment.getRefundMemo() : null;
            if (refundedAt.isAfter(LocalDate.now(CENTER_ZONE)) || refundedAt.isBefore(payment.getPaidAt())) {
                throw new ApiException(ErrorCode.INVALID_PAYMENT_DATE);
            }
            payment.markRefunded(refundedAt, memo, now);
        }
        activityLogRecorder.record(
                request.refunded() ? ActivityAction.PAYMENT_REFUND_CHECKED : ActivityAction.PAYMENT_REFUND_UNCHECKED,
                callerId, pass.getMemberId(), "PASS_PAYMENT", payment.getId(),
                "이용권 #" + passId + "의 결제 " + won(payment.getAmount()) + "("
                        + payment.getMethod() + ") 기록 환불 " + (request.refunded() ? "체크" : "체크 해제"),
                ActivityChange.ifChanged("refunded", beforeRefunded, payment.isRefunded()),
                ActivityChange.ifChanged("refundedAt", beforeRefundedAt, payment.getRefundedAt()),
                ActivityChange.ifChanged("refundMemo", beforeRefundMemo, payment.getRefundMemo()));
        return toResponses(List.of(payment)).get(0);
    }

    // ===== 공통 =====

    private Pass lockPass(Long passId) {
        return passRepository.findByIdForUpdate(passId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    /** 환불된 이용권은 결제 기록을 남기거나 고칠 수 없다. */
    private static void requireNotRefunded(Pass pass) {
        if (pass.isRefunded()) {
            throw new ApiException(ErrorCode.PASS_REFUNDED);
        }
    }

    private PassPayment findPayment(Long passId, Long paymentId) {
        return paymentRepository.findByIdAndPassId(paymentId, passId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }

    private static String won(long amount) {
        return String.format("%,d원", amount);
    }

    private static void requireNotFuture(LocalDate date) {
        if (date.isAfter(LocalDate.now(CENTER_ZONE))) {
            throw new ApiException(ErrorCode.INVALID_PAYMENT_DATE);
        }
    }

    private static LocalDate parseDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new ApiException(ErrorCode.INVALID_INPUT);
        }
    }

    /** 이용권, 상품, 회원, 기록한 관리자를 한 번에 조회해 응답으로 바꾼다. */
    private List<PassPaymentResponse> toResponses(List<PassPayment> payments) {
        Set<Long> passIds = payments.stream().map(PassPayment::getPassId).collect(Collectors.toSet());
        Map<Long, Pass> passes = passRepository.findAllById(passIds).stream()
                .collect(Collectors.toMap(Pass::getId, Function.identity()));
        Set<Long> productIds = new HashSet<>();
        Set<Long> memberIds = new HashSet<>();
        passes.values().forEach(pass -> {
            productIds.add(pass.getPassProductId());
            memberIds.add(pass.getMemberId());
        });
        payments.forEach(payment -> memberIds.add(payment.getRecordedBy()));
        Map<Long, PassProduct> products = productRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(PassProduct::getId, Function.identity()));
        Map<Long, Member> members = memberRepository.findAllById(memberIds).stream()
                .collect(Collectors.toMap(Member::getId, Function.identity()));
        return payments.stream().map(payment -> {
            Pass pass = passes.get(payment.getPassId());
            return PassPaymentResponse.of(payment, pass, products.get(pass.getPassProductId()),
                    members.get(pass.getMemberId()), members.get(payment.getRecordedBy()));
        }).toList();
    }
}

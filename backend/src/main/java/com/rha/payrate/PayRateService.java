package com.rha.payrate;

import com.rha.common.error.ApiException;
import com.rha.common.error.ErrorCode;
import com.rha.common.validation.RequestValidator;
import com.rha.lessontype.LessonType;
import com.rha.lessontype.LessonTypeRepository;
import com.rha.member.AdminGuard;
import com.rha.member.Member;
import com.rha.member.MemberRepository;
import com.rha.member.MemberRole;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 강사의 수업 유형별 수당 단가(관리자 전용). 변경은 이후에 계산하는 정산부터 적용된다. */
@Service
public class PayRateService {

    private final PayRateRepository payRateRepository;
    private final LessonTypeRepository lessonTypeRepository;
    private final MemberRepository memberRepository;
    private final AdminGuard adminGuard;
    private final RequestValidator requestValidator;

    public PayRateService(PayRateRepository payRateRepository, LessonTypeRepository lessonTypeRepository,
            MemberRepository memberRepository, AdminGuard adminGuard, RequestValidator requestValidator) {
        this.payRateRepository = payRateRepository;
        this.lessonTypeRepository = lessonTypeRepository;
        this.memberRepository = memberRepository;
        this.adminGuard = adminGuard;
        this.requestValidator = requestValidator;
    }

    /** 단가가 설정된 수업 유형만 수업 유형 id 오름차순으로 반환한다. 강사가 아니면 404이다. */
    @Transactional(readOnly = true)
    public PayRatesResponse getPayRates(Long callerId, Long memberId) {
        adminGuard.requireAdmin(callerId);
        Member instructor = memberRepository.findById(memberId)
                .filter(member -> member.isRole(MemberRole.INSTRUCTOR))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        return toResponse(instructor, payRateRepository.findByInstructorIdOrderByLessonTypeIdAsc(memberId));
    }

    /**
     * 단가 목록으로 통째로 교체한다. 확인 순서: 관리자(403) → 형식(400) → 회원(404)
     * → 강사 아님(400, INVALID_INSTRUCTOR) → 목록 오류(400, INVALID_PAY_RATE).
     */
    @Transactional
    public PayRatesResponse updatePayRates(Long callerId, Long memberId, PayRatesRequest request) {
        adminGuard.requireAdmin(callerId);
        requestValidator.validate(request);
        Member instructor = memberRepository.findById(memberId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
        if (!instructor.isRole(MemberRole.INSTRUCTOR)) {
            throw new ApiException(ErrorCode.INVALID_INSTRUCTOR);
        }

        Set<Long> typeIds = new HashSet<>();
        for (PayRatesRequest.PayRateInput input : request.payRates()) {
            if (!typeIds.add(input.lessonTypeId())) {
                throw new ApiException(ErrorCode.INVALID_PAY_RATE);
            }
        }
        if (lessonTypeRepository.findAllById(typeIds).size() != typeIds.size()) {
            throw new ApiException(ErrorCode.INVALID_PAY_RATE);
        }

        Instant now = Instant.now();
        payRateRepository.deleteAllOf(memberId);
        List<PayRate> saved = payRateRepository.saveAll(request.payRates().stream()
                .map(input -> PayRate.of(memberId, input.lessonTypeId(), input.unitPrice(), now)).toList());
        return toResponse(instructor, saved.stream()
                .sorted((a, b) -> a.getLessonTypeId().compareTo(b.getLessonTypeId())).toList());
    }

    private PayRatesResponse toResponse(Member instructor, List<PayRate> rates) {
        Map<Long, LessonType> types = lessonTypeRepository
                .findAllById(rates.stream().map(PayRate::getLessonTypeId).toList()).stream()
                .collect(Collectors.toMap(LessonType::getId, Function.identity()));
        List<PayRatesResponse.PayRateResponse> content = rates.stream().map(rate -> {
            LessonType type = types.get(rate.getLessonTypeId());
            return new PayRatesResponse.PayRateResponse(
                    new PayRatesResponse.LessonTypeSummary(type.getId(), type.getCode(), type.getName()),
                    rate.getUnitPrice());
        }).toList();
        return new PayRatesResponse(new PayRatesResponse.InstructorSummary(instructor.getId(), instructor.getName()),
                content);
    }
}

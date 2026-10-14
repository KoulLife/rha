package com.rha.lessontype;

import com.rha.common.error.ApiException;
import com.rha.common.error.ErrorCode;
import com.rha.common.paging.PageInfo;
import com.rha.common.validation.RequestValidator;
import com.rha.member.AdminGuard;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LessonTypeService {

    private final LessonTypeRepository lessonTypeRepository;
    private final AdminGuard adminGuard;
    private final RequestValidator requestValidator;

    public LessonTypeService(LessonTypeRepository lessonTypeRepository, AdminGuard adminGuard,
            RequestValidator requestValidator) {
        this.lessonTypeRepository = lessonTypeRepository;
        this.adminGuard = adminGuard;
        this.requestValidator = requestValidator;
    }

    /** 등록 순(id 오름차순). {@code active}가 null이면 활성·비활성 유형을 모두 반환한다. */
    @Transactional(readOnly = true)
    public LessonTypePageResponse getLessonTypes(Boolean active, int page, int size) {
        PageRequest pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Order.asc("id")));
        Page<LessonType> result = active == null ? lessonTypeRepository.findAll(pageable)
                : lessonTypeRepository.findByActive(active, pageable);
        List<LessonTypeResponse> content = result.getContent().stream().map(LessonTypeResponse::from).toList();
        return new LessonTypePageResponse(content, PageInfo.of(result));
    }

    @Transactional
    public LessonTypeResponse createLessonType(Long callerId, LessonTypeCreateRequest request) {
        adminGuard.requireAdmin(callerId);
        requestValidator.validate(request);

        if (lessonTypeRepository.existsByCode(request.code())) {
            throw new ApiException(ErrorCode.LESSON_TYPE_CODE_DUPLICATED);
        }
        LessonType type = LessonType.create(request.code(), request.name(), request.capacity(), Instant.now());
        try {
            return LessonTypeResponse.from(lessonTypeRepository.saveAndFlush(type));
        } catch (DataIntegrityViolationException e) {
            // existsByCode 이후 같은 코드가 동시에 등록된 경우
            throw new ApiException(ErrorCode.LESSON_TYPE_CODE_DUPLICATED);
        }
    }

    @Transactional
    public LessonTypeResponse updateLessonType(Long callerId, Long lessonTypeId, LessonTypeUpdateRequest request) {
        adminGuard.requireAdmin(callerId);
        requestValidator.validate(request);
        if (!request.hasAnyField()) {
            throw new ApiException(ErrorCode.INVALID_INPUT);
        }
        LessonType type = lessonTypeRepository.findById(lessonTypeId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));

        type.update(request.name(), request.capacity(), request.active(), Instant.now());
        return LessonTypeResponse.from(type);
    }
}

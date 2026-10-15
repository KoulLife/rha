package com.rha.passproduct;

import com.rha.common.error.ApiException;
import com.rha.common.error.ErrorCode;
import com.rha.common.paging.LikePattern;
import com.rha.common.paging.PageInfo;
import com.rha.common.validation.RequestValidator;
import com.rha.member.AdminGuard;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이용권 상품 관리(관리자 전용). 발급된 이용권이 있는 상품의 삭제 거절(PASS_PRODUCT_IN_USE)과
 * {@code applyToIssued}의 발급 이용권 반영은 이용권 기능이 구현될 때 추가한다.
 */
@Service
public class PassProductService {

    private final PassProductRepository passProductRepository;
    private final AdminGuard adminGuard;
    private final RequestValidator requestValidator;

    public PassProductService(PassProductRepository passProductRepository, AdminGuard adminGuard,
            RequestValidator requestValidator) {
        this.passProductRepository = passProductRepository;
        this.adminGuard = adminGuard;
        this.requestValidator = requestValidator;
    }

    /** 등록 최신순(같으면 id 내림차순). {@code page}는 1부터 시작한다. */
    @Transactional(readOnly = true)
    public PassProductPageResponse getPassProducts(Long callerId, String keyword, int page, int size) {
        adminGuard.requireAdmin(callerId);

        String pattern = LikePattern.contains(keyword);
        Specification<PassProduct> spec = (root, query, cb) -> pattern == null ? cb.conjunction()
                : cb.like(cb.lower(root.get("name")), pattern, '\\');
        PageRequest pageable = PageRequest.of(page - 1, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        Page<PassProduct> result = passProductRepository.findAll(spec, pageable);

        List<PassProductResponse> content = result.getContent().stream().map(PassProductResponse::from).toList();
        return new PassProductPageResponse(content, PageInfo.of(result));
    }

    @Transactional(readOnly = true)
    public PassProductResponse getPassProduct(Long callerId, Long passProductId) {
        adminGuard.requireAdmin(callerId);
        return PassProductResponse.from(find(passProductId));
    }

    @Transactional
    public PassProductResponse createPassProduct(Long callerId, PassProductCreateRequest request) {
        adminGuard.requireAdmin(callerId);
        requestValidator.validate(request);

        PassProduct product = PassProduct.create(request.name(), request.price(), request.totalCount(),
                request.validityDays(), request.description(), Instant.now());
        return PassProductResponse.from(passProductRepository.save(product));
    }

    /** 확인 순서는 관리자(403) → 입력(400) → 상품 존재(404)이다. */
    @Transactional
    public PassProductUpdateResponse updatePassProduct(Long callerId, Long passProductId,
            PassProductUpdateRequest request) {
        adminGuard.requireAdmin(callerId);
        requestValidator.validate(request);
        if (!request.hasAnyField()) {
            throw new ApiException(ErrorCode.INVALID_INPUT);
        }
        PassProduct product = find(passProductId);

        product.update(request.getName(), request.getPrice(), request.getTotalCount(), request.getValidityDays(),
                request.isDescriptionSpecified(), request.getDescription(), Instant.now());
        return new PassProductUpdateResponse(PassProductResponse.from(product), 0);
    }

    @Transactional
    public void deletePassProduct(Long callerId, Long passProductId) {
        adminGuard.requireAdmin(callerId);
        passProductRepository.delete(find(passProductId));
    }

    private PassProduct find(Long passProductId) {
        return passProductRepository.findById(passProductId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }
}

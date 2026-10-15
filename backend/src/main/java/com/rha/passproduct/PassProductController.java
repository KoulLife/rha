package com.rha.passproduct;

import com.rha.auth.AuthenticatedMember;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 쓰기 API의 본문 검증은 관리자 여부를 확인한 뒤에 하므로 `@Valid`를 붙이지 않는다. */
@RestController
@RequestMapping("/pass-products")
public class PassProductController {

    private final PassProductService passProductService;

    public PassProductController(PassProductService passProductService) {
        this.passProductService = passProductService;
    }

    @GetMapping
    public PassProductPageResponse getPassProducts(@AuthenticationPrincipal AuthenticatedMember me,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) @Size(max = 100) String keyword) {
        return passProductService.getPassProducts(me.id(), keyword, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PassProductResponse createPassProduct(@AuthenticationPrincipal AuthenticatedMember me,
            @RequestBody PassProductCreateRequest request) {
        return passProductService.createPassProduct(me.id(), request);
    }

    @GetMapping("/{passProductId}")
    public PassProductResponse getPassProduct(@AuthenticationPrincipal AuthenticatedMember me,
            @PathVariable Long passProductId) {
        return passProductService.getPassProduct(me.id(), passProductId);
    }

    @PatchMapping("/{passProductId}")
    public PassProductUpdateResponse updatePassProduct(@AuthenticationPrincipal AuthenticatedMember me,
            @PathVariable Long passProductId, @RequestBody PassProductUpdateRequest request) {
        return passProductService.updatePassProduct(me.id(), passProductId, request);
    }

    @DeleteMapping("/{passProductId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePassProduct(@AuthenticationPrincipal AuthenticatedMember me,
            @PathVariable Long passProductId) {
        passProductService.deletePassProduct(me.id(), passProductId);
    }
}

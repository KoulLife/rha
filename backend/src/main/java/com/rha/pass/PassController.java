package com.rha.pass;

import com.rha.auth.AuthenticatedMember;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 쓰기 API의 본문 검증은 관리자 여부를 확인한 뒤에 하므로 `@Valid`를 붙이지 않는다. */
@RestController
public class PassController {

    private final PassService passService;

    public PassController(PassService passService) {
        this.passService = passService;
    }

    @GetMapping("/passes")
    public PassPageResponse getPasses(@AuthenticationPrincipal AuthenticatedMember me,
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) @Size(max = 50) String keyword,
            @RequestParam(required = false) Long instructorId,
            @RequestParam(required = false) Long passProductId,
            @RequestParam(required = false) PassStatus status,
            @RequestParam(required = false) PaymentStatus paymentStatus,
            @RequestParam(required = false) Instant createdFrom,
            @RequestParam(required = false) Instant createdTo,
            @RequestParam(defaultValue = "createdAt")
            @Pattern(regexp = "createdAt|endDate|totalCount|remainingCount") String sortBy,
            @RequestParam(defaultValue = "DESC") Sort.Direction order,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return passService.getPasses(me.id(), new PassService.Filter(memberId, keyword, instructorId,
                passProductId, status, paymentStatus, createdFrom, createdTo), sortBy, order, page, size);
    }

    @PostMapping("/passes")
    @ResponseStatus(HttpStatus.CREATED)
    public PassResponse createPass(@AuthenticationPrincipal AuthenticatedMember me,
            @RequestBody PassCreateRequest request) {
        return passService.createPass(me.id(), request);
    }

    @GetMapping("/passes/{passId}")
    public PassResponse getPass(@AuthenticationPrincipal AuthenticatedMember me, @PathVariable Long passId) {
        return passService.getPass(me.id(), passId);
    }

    @PatchMapping("/passes/{passId}")
    public PassResponse updatePass(@AuthenticationPrincipal AuthenticatedMember me, @PathVariable Long passId,
            @RequestBody PassUpdateRequest request) {
        return passService.updatePass(me.id(), passId, request);
    }

    @GetMapping("/passes/{passId}/pauses")
    public PassPausePageResponse getPassPauses(@AuthenticationPrincipal AuthenticatedMember me,
            @PathVariable Long passId,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return passService.getPassPauses(me.id(), passId, page, size);
    }

    @PostMapping("/passes/{passId}/pauses")
    @ResponseStatus(HttpStatus.CREATED)
    public PassPauseResponse createPassPause(@AuthenticationPrincipal AuthenticatedMember me,
            @PathVariable Long passId, @RequestBody PassPauseCreateRequest request) {
        return passService.createPassPause(me.id(), passId, request);
    }

    @PostMapping("/passes/{passId}/pauses/{pauseId}/terminations")
    public PassPauseResponse terminatePassPause(@AuthenticationPrincipal AuthenticatedMember me,
            @PathVariable Long passId, @PathVariable Long pauseId) {
        return passService.terminatePassPause(me.id(), passId, pauseId);
    }

    @GetMapping("/members/me/passes")
    public PassPageResponse getMyPasses(@AuthenticationPrincipal AuthenticatedMember me,
            @RequestParam(required = false) PassStatus status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return passService.getMyPasses(me.id(), status, page, size);
    }
}

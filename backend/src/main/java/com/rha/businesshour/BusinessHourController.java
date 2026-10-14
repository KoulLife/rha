package com.rha.businesshour;

import com.rha.auth.AuthenticatedMember;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 쓰기 API의 본문 검증은 관리자 여부를 확인한 뒤에 하므로 `@Valid`를 붙이지 않는다. */
@RestController
@RequestMapping("/business-hours")
public class BusinessHourController {

    private final BusinessHourService businessHourService;

    public BusinessHourController(BusinessHourService businessHourService) {
        this.businessHourService = businessHourService;
    }

    @GetMapping
    public BusinessHoursResponse getBusinessHours() {
        return businessHourService.getBusinessHours();
    }

    @PutMapping
    public BusinessHoursResponse updateBusinessHours(@AuthenticationPrincipal AuthenticatedMember me,
            @RequestBody BusinessHoursRequest request) {
        return businessHourService.updateBusinessHours(me.id(), request);
    }
}

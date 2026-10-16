package com.rha.common.error;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 공통 에러 처리 검증용. 테스트 클래스패스에만 존재한다. */
@RestController
@RequestMapping("/test-errors")
class ErrorTestController {

    record Body(@NotBlank String name, @Email String email) {
    }

    @GetMapping("/api-exception")
    void apiException() {
        throw new ApiException(ErrorCode.EMAIL_ALREADY_EXISTS);
    }

    @GetMapping("/ok")
    Body ok() {
        return new Body("이름", "a@example.com");
    }

    @GetMapping("/boom")
    void boom() {
        throw new IllegalStateException("내부 구현 정보");
    }

    @PostMapping("/validation")
    void validation(@Valid @RequestBody Body body) {
    }
}

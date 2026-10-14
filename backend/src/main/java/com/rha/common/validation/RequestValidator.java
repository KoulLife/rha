package com.rha.common.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * 컨트롤러의 {@code @Valid} 대신 서비스에서 요청을 검증한다.
 * 호출자의 권한(403)을 확인한 뒤에 입력 오류(400)를 내야 하는 API가 사용한다.
 * 위반이 있으면 {@link ConstraintViolationException}을 던지고, 400 INVALID_INPUT으로 응답된다.
 */
@Component
public class RequestValidator {

    private final Validator validator;

    public RequestValidator(Validator validator) {
        this.validator = validator;
    }

    public void validate(Object request) {
        Set<ConstraintViolation<Object>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }
}

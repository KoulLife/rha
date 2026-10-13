package com.rha.common.error;

import java.net.URI;
import java.time.Instant;
import org.springframework.http.ProblemDetail;

/** RFC 7807 에러 본문. `code`와 `timestamp`는 확장 필드로 담는다. */
public final class ProblemDetails {

    private ProblemDetails() {
    }

    public static ProblemDetail of(ErrorCode errorCode, String detail, String instance) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(errorCode.status(), detail);
        problem.setInstance(URI.create(instance));
        problem.setProperty("code", errorCode.name());
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}

package com.rha.common.error;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.lang.Nullable;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    record InvalidParam(String field, String reason) {
    }

    /** 위에서 따로 다루지 않은 예외. 내부 정보는 응답에 담지 않고 로그에만 남긴다. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception e, HttpServletRequest request) throws Exception {
        if (e instanceof AccessDeniedException || e instanceof AuthenticationException) {
            throw e; // Spring Security가 401/403으로 처리한다.
        }
        log.error("처리하지 못한 예외: {} {}", request.getMethod(), request.getRequestURI(), e);
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        return ResponseEntity.status(errorCode.status())
                .body(problem(errorCode, errorCode.defaultMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApiException(ApiException e, HttpServletRequest request) {
        ErrorCode errorCode = e.errorCode();
        ProblemDetail problem = problem(errorCode, e.getMessage(), request.getRequestURI());
        if (!e.details().isEmpty()) {
            problem.setProperty("invalidParams", e.details().stream()
                    .map(d -> new InvalidParam(d.field(), d.reason())).toList());
        }
        return ResponseEntity.status(errorCode.status()).body(problem);
    }

    /** 서비스에서 직접 Bean Validation을 수행한 경우(권한 확인 뒤에 입력을 검증해야 하는 API). */
    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException e,
            HttpServletRequest request) {
        List<InvalidParam> invalidParams = e.getConstraintViolations().stream()
                .map(v -> new InvalidParam(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        ProblemDetail problem = problem(ErrorCode.INVALID_INPUT, ErrorCode.INVALID_INPUT.defaultMessage(),
                request.getRequestURI());
        problem.setProperty("invalidParams", invalidParams);
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.status()).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<InvalidParam> invalidParams = e.getBindingResult().getFieldErrors().stream()
                .map(error -> new InvalidParam(error.getField(), error.getDefaultMessage()))
                .toList();
        ProblemDetail problem = problem(ErrorCode.INVALID_INPUT, ErrorCode.INVALID_INPUT.defaultMessage(),
                requestUri(request));
        problem.setProperty("invalidParams", invalidParams);
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.status()).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException e,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = problem(ErrorCode.INVALID_INPUT, ErrorCode.INVALID_INPUT.defaultMessage(),
                requestUri(request));
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.status()).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException e,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<InvalidParam> invalidParams = e.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new InvalidParam(result.getMethodParameter().getParameterName(),
                                error.getDefaultMessage())))
                .toList();
        ProblemDetail problem = problem(ErrorCode.INVALID_INPUT, ErrorCode.INVALID_INPUT.defaultMessage(),
                requestUri(request));
        problem.setProperty("invalidParams", invalidParams);
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.status()).body(problem);
    }

    /** 숫자가 아닌 경로 변수, 정의되지 않은 enum 값 등 파라미터 타입이 맞지 않는 경우. */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException e, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {
        String field = e instanceof MethodArgumentTypeMismatchException mismatch ? mismatch.getName()
                : e.getPropertyName();
        ProblemDetail problem = problem(ErrorCode.INVALID_INPUT, ErrorCode.INVALID_INPUT.defaultMessage(),
                requestUri(request));
        if (field != null) {
            problem.setProperty("invalidParams", List.of(new InvalidParam(field, "값의 형식이 올바르지 않습니다.")));
        }
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.status()).body(problem);
    }

    /**
     * 404(없는 주소), 405, 406, 415 등 Spring MVC가 직접 만드는 오류도 `code`와 `timestamp`를 담는 본문으로 바꾼다.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception e, @Nullable Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ErrorCode errorCode = switch (status.value()) {
            case 400 -> ErrorCode.INVALID_INPUT;
            case 404 -> ErrorCode.NOT_FOUND;
            case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
            case 406 -> ErrorCode.NOT_ACCEPTABLE;
            case 415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
            default -> ErrorCode.INTERNAL_SERVER_ERROR;
        };
        if (status.is5xxServerError()) {
            log.error("처리하지 못한 예외: {}", requestUri(request), e);
        }
        ProblemDetail problem = problem(errorCode, errorCode.defaultMessage(), requestUri(request));
        problem.setStatus(status.value());
        return new ResponseEntity<>(problem, headers, status);
    }

    private ProblemDetail problem(ErrorCode errorCode, String detail, String instance) {
        return ProblemDetails.of(errorCode, detail, instance);
    }

    private String requestUri(WebRequest request) {
        return ((ServletWebRequest) request).getRequest().getRequestURI();
    }
}

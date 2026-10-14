package com.rha.member;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;

/** 형식이 맞아도 목록에 없거나 중복된 코드는 서비스에서 INVALID_PERMISSION으로 처리한다. */
public record MemberPermissionsRequest(
        @NotNull List<@NotNull @Pattern(regexp = "^[A-Z][A-Z_]*(:[A-Z][A-Z0-9_]*)?$",
                message = "권한 코드 형식이 올바르지 않습니다.") String> permissions) {
}

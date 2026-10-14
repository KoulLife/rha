package com.rha.member;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** WITHDRAWN, RESIGNED는 지정할 수 없다. */
public record MemberStatusChangeRequest(
        @NotBlank @Pattern(regexp = "^(ACTIVE|INACTIVE)$", message = "ACTIVE 또는 INACTIVE만 지정할 수 있습니다.")
        String status) {
}

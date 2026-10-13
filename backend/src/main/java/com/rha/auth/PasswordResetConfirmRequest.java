package com.rha.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmRequest(
        @NotBlank String token,
        @NotBlank @Size(min = 8, max = 64, message = "비밀번호는 8자 이상 64자 이하여야 합니다.") String newPassword) {
}

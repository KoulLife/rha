package com.rha.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record SignUpRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 8, max = 64, message = "비밀번호는 8자 이상 64자 이하여야 합니다.") String password,
        @NotBlank @Size(max = 50) String name,
        @NotBlank @Pattern(regexp = "^01[0-9]{8,9}$", message = "하이픈 없는 휴대폰 번호여야 합니다.") String phoneNumber,
        @NotNull LocalDate birthDate,
        @Size(max = 200) String address,
        @NotBlank String emailVerificationToken) {
}

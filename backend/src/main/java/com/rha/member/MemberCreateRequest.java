package com.rha.member;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record MemberCreateRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 50) String name,
        @NotBlank @Pattern(regexp = "^01[0-9]{8,9}$", message = "하이픈 없는 휴대폰 번호여야 합니다.") String phoneNumber,
        @NotNull LocalDate birthDate,
        @Size(max = 200) String address,
        @NotBlank @Pattern(regexp = "^(ADMIN|INSTRUCTOR)$", message = "ADMIN 또는 INSTRUCTOR만 등록할 수 있습니다.")
        String role) {
}

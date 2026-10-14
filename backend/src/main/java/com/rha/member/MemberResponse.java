package com.rha.member;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record MemberResponse(
        Long id,
        String email,
        MemberRole role,
        MemberStatus status,
        List<String> permissions,
        LocalDate resignationDate,
        String name,
        String phoneNumber,
        LocalDate birthDate,
        String address,
        Instant createdAt) {

    /** 권한이 없는 새 계정 등, 부여된 권한이 없음을 아는 경우에 쓴다. */
    public static MemberResponse from(Member member) {
        return from(member, List.of());
    }

    public static MemberResponse from(Member member, List<String> permissions) {
        return new MemberResponse(member.getId(), member.getEmail(), member.getRole(), member.getStatus(),
                permissions, member.getResignationDate(), member.getName(), member.getPhoneNumber(),
                member.getBirthDate(), member.getAddress(), member.getCreatedAt());
    }
}

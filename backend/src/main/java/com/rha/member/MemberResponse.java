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
        String name,
        String phoneNumber,
        LocalDate birthDate,
        String address,
        Instant createdAt) {

    public static MemberResponse from(Member member) {
        return new MemberResponse(member.getId(), member.getEmail(), member.getRole(), member.getStatus(),
                List.of(), member.getName(), member.getPhoneNumber(), member.getBirthDate(),
                member.getAddress(), member.getCreatedAt());
    }
}

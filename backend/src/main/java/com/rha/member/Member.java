package com.rha.member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "members")
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    private String passwordHash;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String phoneNumber;

    @Column(nullable = false)
    private LocalDate birthDate;

    private String address;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberStatus status;

    private Instant withdrawnAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected Member() {
    }

    /** 회원 본인이 가입한 계정. 항상 MEMBER 역할의 ACTIVE 상태로 생성된다. */
    public static Member signUp(String email, String passwordHash, String name, String phoneNumber,
            LocalDate birthDate, String address, Instant now) {
        Member member = new Member();
        member.email = email;
        member.passwordHash = passwordHash;
        member.name = name;
        member.phoneNumber = phoneNumber;
        member.birthDate = birthDate;
        member.address = address;
        member.role = MemberRole.MEMBER;
        member.status = MemberStatus.ACTIVE;
        member.createdAt = now;
        member.updatedAt = now;
        return member;
    }

    /** 관리자가 등록한 계정. 비밀번호 없이 ACTIVE 상태로 생성되며, 초대 메일로 비밀번호를 설정한다. */
    public static Member invite(String email, String name, String phoneNumber, LocalDate birthDate,
            String address, MemberRole role, Instant now) {
        Member member = new Member();
        member.email = email;
        member.name = name;
        member.phoneNumber = phoneNumber;
        member.birthDate = birthDate;
        member.address = address;
        member.role = role;
        member.status = MemberStatus.ACTIVE;
        member.createdAt = now;
        member.updatedAt = now;
        return member;
    }

    /**
     * 본인 정보를 부분 수정한다. null인 필드는 바꾸지 않는다.
     * 주소는 null이 삭제를 뜻하므로 {@code changeAddress}로 변경 여부를 따로 받는다.
     */
    public void updateProfile(String name, String phoneNumber, LocalDate birthDate,
            boolean changeAddress, String address, Instant now) {
        if (name != null) {
            this.name = name;
        }
        if (phoneNumber != null) {
            this.phoneNumber = phoneNumber;
        }
        if (birthDate != null) {
            this.birthDate = birthDate;
        }
        if (changeAddress) {
            this.address = address;
        }
        this.updatedAt = now;
    }

    public void changePassword(String passwordHash, Instant now) {
        this.passwordHash = passwordHash;
        this.updatedAt = now;
    }

    /** 본인 탈퇴. 데이터는 지우지 않고 상태만 WITHDRAWN으로 바꾼다. */
    public void withdraw(Instant now) {
        this.status = MemberStatus.WITHDRAWN;
        this.withdrawnAt = now;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isActive() {
        return status == MemberStatus.ACTIVE;
    }

    public String getName() {
        return name;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public String getAddress() {
        return address;
    }

    public boolean isRole(MemberRole role) {
        return this.role == role;
    }

    public MemberRole getRole() {
        return role;
    }

    public MemberStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

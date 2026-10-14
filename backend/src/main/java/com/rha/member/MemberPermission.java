package com.rha.member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;

/** 강사에게 명시적으로 부여된 권한. 관리자는 암묵적으로 모든 권한을 가지므로 행이 없다. */
@Entity
@Table(name = "member_permissions")
@IdClass(MemberPermission.Key.class)
public class MemberPermission {

    public record Key(Long memberId, String permissionCode) implements Serializable {
    }

    @Id
    private Long memberId;

    @Id
    private String permissionCode;

    @Column(nullable = false)
    private Long grantedBy;

    @Column(nullable = false)
    private Instant createdAt;

    protected MemberPermission() {
    }

    public Long getMemberId() {
        return memberId;
    }

    public String getPermissionCode() {
        return permissionCode;
    }
}

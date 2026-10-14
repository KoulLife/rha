package com.rha.member;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberPermissionRepository extends JpaRepository<MemberPermission, MemberPermission.Key> {

    List<MemberPermission> findByMemberIdInOrderByPermissionCode(Collection<Long> memberIds);

    boolean existsByMemberIdAndPermissionCode(Long memberId, String permissionCode);

    /** 해당 회원에게 부여된 권한을 모두 회수한다. */
    @Modifying
    @Query("delete from MemberPermission p where p.memberId = :memberId")
    int deleteAllOf(@Param("memberId") Long memberId);
}

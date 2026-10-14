package com.rha.member;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberPermissionRepository extends JpaRepository<MemberPermission, MemberPermission.Key> {

    List<MemberPermission> findByMemberIdInOrderByPermissionCode(Collection<Long> memberIds);

    boolean existsByMemberIdAndPermissionCode(Long memberId, String permissionCode);
}

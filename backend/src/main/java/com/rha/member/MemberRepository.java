package com.rha.member;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, Long>, JpaSpecificationExecutor<Member> {

    boolean existsByEmail(String email);

    Optional<Member> findByEmail(String email);

    org.springframework.data.domain.Page<Member> findByRoleAndStatus(MemberRole role, MemberStatus status,
            org.springframework.data.domain.Pageable pageable);

    /** 수업 시간 충돌 검사와 저장 사이에 같은 강사의 수업이 끼어들지 못하도록 행을 잠근다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Member m where m.id = :id")
    Optional<Member> findByIdForUpdate(@Param("id") Long id);
}

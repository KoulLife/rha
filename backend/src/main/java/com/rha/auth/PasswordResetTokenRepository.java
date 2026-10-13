package com.rha.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    /** 해당 회원이 발급받고 아직 사용하지 않은 토큰을 모두 지운다. */
    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.memberId = :memberId AND t.usedAt IS NULL")
    void deleteUnusedByMemberId(@Param("memberId") Long memberId);
}

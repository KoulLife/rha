package com.rha.auth;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /** 사용하지 않았고 만료되지 않은 토큰만 사용 처리한다. 처리된 행 수(0 또는 1)를 반환한다. */
    @Modifying
    @Query("""
            UPDATE PasswordResetToken t SET t.usedAt = :now
            WHERE t.tokenHash = :tokenHash AND t.usedAt IS NULL AND t.expiresAt > :now
            """)
    int markUsed(@Param("tokenHash") String tokenHash, @Param("now") Instant now);

    /** 해당 회원이 발급받고 아직 사용하지 않은 토큰을 모두 지운다. */
    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.memberId = :memberId AND t.usedAt IS NULL")
    void deleteUnusedByMemberId(@Param("memberId") Long memberId);
}

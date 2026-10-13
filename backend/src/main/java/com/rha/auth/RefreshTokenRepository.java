package com.rha.auth;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** 만료되지 않았고 폐기되지 않은 토큰만 폐기한다. 폐기된 행 수(0 또는 1)를 반환한다. */
    @Modifying
    @Query("""
            update RefreshToken t set t.revokedAt = :now
            where t.tokenHash = :tokenHash and t.revokedAt is null and t.expiresAt > :now
            """)
    int revoke(@Param("tokenHash") String tokenHash, @Param("now") Instant now);
}

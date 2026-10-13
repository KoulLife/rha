package com.rha.auth;

import com.rha.member.Member;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Access Token(JWT)을 발급한다. 서버는 토큰의 역할(role)을 기준으로 권한을 판단한다. */
@Component
public class AccessTokenProvider {

    private final SecretKey key;
    private final Duration ttl;

    public AccessTokenProvider(@Value("${rha.auth.jwt.secret}") String secret,
            @Value("${rha.auth.access-token-ttl:1h}") Duration ttl) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttl = ttl;
    }

    public String issue(Member member, Instant now) {
        return Jwts.builder()
                .subject(String.valueOf(member.getId()))
                .claim("role", member.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    public long expiresInSeconds() {
        return ttl.toSeconds();
    }
}

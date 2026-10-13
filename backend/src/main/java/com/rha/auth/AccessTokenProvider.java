package com.rha.auth;

import com.rha.member.Member;
import com.rha.member.MemberRole;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Access Token(JWT)을 발급하고 검증한다. 서버는 토큰의 역할(role)을 기준으로 권한을 판단한다. */
@Component
public class AccessTokenProvider {

    private static final String ROLE_CLAIM = "role";

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
                .claim(ROLE_CLAIM, member.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key)
                .compact();
    }

    /** 서명, 만료, 형식이 모두 올바른 토큰만 회원 정보를 돌려준다. 그 외에는 이유를 구분하지 않고 비어 있다. */
    public Optional<AuthenticatedMember> parse(String token) {
        try {
            var claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return Optional.of(new AuthenticatedMember(
                    Long.valueOf(claims.getSubject()),
                    MemberRole.valueOf(claims.get(ROLE_CLAIM, String.class))));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public long expiresInSeconds() {
        return ttl.toSeconds();
    }
}

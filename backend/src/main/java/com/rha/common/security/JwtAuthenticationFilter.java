package com.rha.common.security;

import com.rha.auth.AccessTokenProvider;
import com.rha.auth.AuthenticatedMember;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * `Authorization: Bearer {accessToken}`을 검증해 인증 정보를 채운다.
 * 토큰이 없거나 유효하지 않으면 인증 정보를 채우지 않고 넘긴다. 인증이 필요한 경로에서는 401로 이어진다.
 *
 * <p>빈으로 등록하지 않는다. `Filter` 빈은 서블릿 컨테이너에 따로 한 번 더 등록되기 때문에,
 * {@link SecurityConfig}에서 시큐리티 필터 체인에만 직접 추가한다.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final AccessTokenProvider accessTokenProvider;

    public JwtAuthenticationFilter(AccessTokenProvider accessTokenProvider) {
        this.accessTokenProvider = accessTokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            accessTokenProvider.parse(header.substring(BEARER_PREFIX.length()).trim())
                    .ifPresent(this::authenticate);
        }
        chain.doFilter(request, response);
    }

    private void authenticate(AuthenticatedMember member) {
        var authentication = new UsernamePasswordAuthenticationToken(
                member, null, List.of(new SimpleGrantedAuthority("ROLE_" + member.role().name())));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}

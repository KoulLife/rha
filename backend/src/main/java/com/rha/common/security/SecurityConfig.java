package com.rha.common.security;

import com.rha.auth.AccessTokenProvider;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    /** 스펙에서 `security: []`로 표시한 API. 새 공개 API를 만들면 여기에 추가한다. */
    private static final String[] PUBLIC_POST_PATHS = {
            "/auth/email-verifications",
            "/auth/email-verifications/confirm",
            "/auth/sign-up",
            "/auth/login",
            "/auth/refresh",
            "/auth/password-reset-requests",
            "/auth/password-resets",
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AccessTokenProvider accessTokenProvider,
            ApiAuthenticationEntryPoint entryPoint) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 오류 디스패치(/error)는 인증 정보가 없어, 막으면 404·500이 401로 바뀐다.
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_POST_PATHS).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handling -> handling.authenticationEntryPoint(entryPoint))
                .addFilterBefore(new JwtAuthenticationFilter(accessTokenProvider),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}

package com.guidepins.config;

import com.guidepins.auth.security.SessionAuthenticationFilter;
import com.guidepins.common.ApiResponse;
import com.guidepins.common.ApiResponseWriter;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.session.NullAuthenticatedSessionStrategy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;

/** 同源 API 的无状态安全链：数据库会话鉴权、CSRF、401/403；底图 Range 不经过此链。 */
@Configuration
public class AuthSecurityConfig {
    @Bean
    Clock authClock() {
        return Clock.systemUTC();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository(@Value("${guidepins.auth.cookie-secure:true}") boolean secure) {
        var repository = new CookieCsrfTokenRepository();
        repository.setCookieName("GUIDEPINS_CSRF");
        repository.setHeaderName("X-CSRF-TOKEN");
        repository.setCookiePath("/");
        repository.setCookieCustomizer(cookie -> cookie.httpOnly(true).secure(secure).sameSite("Lax"));
        return repository;
    }

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, CsrfTokenRepository csrf,
                                   SessionAuthenticationFilter sessions, ApiResponseWriter responses) throws Exception {
        http.securityMatcher("/api/**")
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .formLogin(login -> login.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                // 身份每次从数据库恢复，CSRF 轮换仅在登录/退出接口显式处理。
                .csrf(config -> config.csrfTokenRepository(csrf)
                        .sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy()))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, error) ->
                                responses.error(response, 401, ApiResponse.messageFor(401)))
                        .accessDeniedHandler((request, response, error) ->
                                responses.error(response, 403, ApiResponse.messageFor(403))))
                .authorizeHttpRequests(access -> access
                        .requestMatchers("/api/auth/csrf", "/api/auth/register", "/api/auth/login",
                                "/api/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/cities", "/api/cities/*/maps", "/api/cities/*/boundary",
                                "/api/maps/*", "/api/maps/*/guides", "/api/maps/*/guides/*", "/api/maps/*/pins/*/guides")
                        .permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(sessions, AnonymousAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    FilterRegistrationBean<SessionAuthenticationFilter> disableServletRegistration(SessionAuthenticationFilter sessions) {
        // 只在安全链中执行一次，避免 Boot 另行注册同一个 Servlet Filter。
        var registration = new FilterRegistrationBean<>(sessions);
        registration.setEnabled(false);
        return registration;
    }
}

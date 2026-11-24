package com.guidepins.auth.security;

import com.guidepins.auth.service.AuthService;
import com.guidepins.common.ApiResponse;
import com.guidepins.common.ApiResponseWriter;
import com.guidepins.auth.domain.AccountIdentity;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** 只为当前请求恢复身份，不创建 HttpSession，不跨请求缓存角色或账号状态。 */
@Component
public class SessionAuthenticationFilter extends OncePerRequestFilter {
    @Autowired
    private AuthService auth;
    @Autowired
    private SessionCookie cookie;
    @Autowired
    private ApiResponseWriter responses;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<AccountIdentity> identity;
        try {
            identity = auth.authenticate(cookie.read(request));
        } catch (RuntimeException error) {
            // 此处发生在 MVC 之前，数据库故障也需返回统一的安全错误信息。
            logger.error("会话身份恢复失败", error);
            responses.error(response, 500, ApiResponse.messageFor(500));
            return;
        }
        identity.ifPresent(user -> {
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.role()))));
            SecurityContextHolder.setContext(context);
        });
        chain.doFilter(request, response);
    }
}

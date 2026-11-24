package com.guidepins.auth.controller;

import com.guidepins.auth.domain.AccountIdentity;
import com.guidepins.auth.security.SessionCookie;
import com.guidepins.auth.service.AuthService;
import com.guidepins.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 认证接口负责请求校验与 Cookie，账号和会话业务交给服务层。 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @Autowired
    private AuthService auth;
    @Autowired
    private SessionCookie cookie;
    @Autowired
    private CsrfTokenRepository csrf;

    @GetMapping("/csrf")
    CsrfResponse csrf(CsrfToken token) {
        // 返回 Spring 的掩码令牌；前端使用 JSON 中的值，不读取 HttpOnly Cookie。
        return new CsrfResponse(token.getHeaderName(), token.getToken());
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    AccountIdentity register(@Valid @RequestBody Credentials request) {
        return auth.register(request.email(), request.phone(), request.password());
    }

    @PostMapping("/login")
    AccountIdentity login(@Valid @RequestBody Credentials body, HttpServletRequest request,
                          HttpServletResponse response) {
        var login = auth.login(body.email(), body.phone(), body.password());
        cookie.write(response, login.token());
        // 自定义数据库会话同样在登录、退出时清除旧防伪令牌，客户端随后重新获取。
        csrf.saveToken(null, request, response);
        return login.user();
    }

    @GetMapping("/me")
    AccountIdentity me(@AuthenticationPrincipal AccountIdentity user) {
        return user;
    }

    @PostMapping("/logout")
    ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        auth.logout(cookie.read(request));
        cookie.clear(response);
        csrf.saveToken(null, request, response);
        return ApiResponse.success(200, "退出成功", null);
    }

    record CsrfResponse(String headerName, String token) {}
}

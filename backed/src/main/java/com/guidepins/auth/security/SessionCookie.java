package com.guidepins.auth.security;

import com.guidepins.auth.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** 会话 Cookie 的唯一读写入口，退出沿用相同路径和安全属性清除。 */
@Component
public class SessionCookie {
    public static final String NAME = "GUIDEPINS_SESSION";

    @Value("${guidepins.auth.cookie-secure:true}")
    private boolean secure;

    public String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (NAME.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    public void write(HttpServletResponse response, String token) {
        set(response, token, AuthService.SESSION_LIFETIME);
    }

    public void clear(HttpServletResponse response) {
        set(response, "", Duration.ZERO);
    }

    private void set(HttpServletResponse response, String value, Duration age) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(NAME, value)
                .path("/").httpOnly(true).secure(secure).sameSite("Lax").maxAge(age).build().toString());
    }
}

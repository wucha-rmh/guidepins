package com.guidepins.auth.controller;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;

/** 注册与登录均选择一种登录标识，先规范化再执行参数校验。 */
public record Credentials(
        @Email @Size(max = 254) String email,
        @Pattern(regexp = "\\+[1-9][0-9]{1,14}") String phone,
        @NotBlank @Size(max = 128) String password) {
    public Credentials {
        email = normalize(email);
        if (email != null) {
            email = email.toLowerCase(Locale.ROOT);
        }
        phone = normalize(phone);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    @Override
    public String toString() {
        return "Credentials[password=已隐藏]";
    }
}

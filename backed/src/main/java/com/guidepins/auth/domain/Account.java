package com.guidepins.auth.domain;

import java.time.Instant;
import java.util.UUID;

/** 认证内部账号，密码编码只用于服务端校验，不直接作为接口响应。 */
public record Account(UUID id, String email, String phone, String passwordHash, String role,
                      String avatarPath, short status, Instant createdAt) {
    public AccountIdentity identity() {
        return new AccountIdentity(id, email, phone, role, avatarPath, createdAt);
    }

    @Override
    public String toString() {
        return "Account[id=" + id + ", role=" + role + ", status=" + status + "]";
    }
}

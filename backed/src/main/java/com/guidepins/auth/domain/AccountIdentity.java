package com.guidepins.auth.domain;

import java.time.Instant;
import java.util.UUID;

/** 返回给本人并存入当前请求的身份，不包含密码编码或会话令牌。 */
public record AccountIdentity(UUID id, String email, String phone, String role,
                              String avatarPath, Instant createdAt) {}

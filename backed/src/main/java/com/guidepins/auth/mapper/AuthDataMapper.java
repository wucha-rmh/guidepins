package com.guidepins.auth.mapper;

import com.guidepins.auth.domain.Account;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** 账号及服务端会话的数据边界；持久层只接收令牌摘要。 */
public interface AuthDataMapper {
    Optional<Account> findByIdentifier(String email, String phone);
    Account createAccount(String email, String phone, String passwordHash);
    void createSession(String tokenHash, UUID userId, Instant createdAt, Instant expiresAt);
    Optional<Account> findActiveSession(String tokenHash, Instant now);
    void deleteSession(String tokenHash);
    int deleteExpiredSessions(Instant now);
}

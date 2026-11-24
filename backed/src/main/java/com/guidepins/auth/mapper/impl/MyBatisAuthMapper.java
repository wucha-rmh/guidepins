package com.guidepins.auth.mapper.impl;

import com.guidepins.auth.domain.Account;
import com.guidepins.auth.mapper.AuthDataMapper;
import com.guidepins.auth.mapper.entity.AccountRow;
import com.guidepins.auth.mapper.mybatis.AuthSqlMapper;
import com.guidepins.exception.ApiException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.http.HttpStatus;

/** 将认证数据库行转换成内部账号，并统一封装会话读写。 */
@Repository
public class MyBatisAuthMapper implements AuthDataMapper {
    @Autowired
    private AuthSqlMapper sql;

    @Override
    public Optional<Account> findByIdentifier(String email, String phone) {
        return Optional.ofNullable(sql.selectByIdentifier(email, phone)).map(MyBatisAuthMapper::account);
    }

    @Override
    public Account createAccount(String email, String phone, String passwordHash) {
        return account(sql.insertAccount(email, phone, passwordHash));
    }

    @Override
    public void createSession(String tokenHash, UUID userId, Instant createdAt, Instant expiresAt) {
        if (sql.insertSession(tokenHash, userId, createdAt, expiresAt) != 1) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "账号不可用");
        }
    }

    @Override
    public Optional<Account> findActiveSession(String tokenHash, Instant now) {
        return Optional.ofNullable(sql.selectActiveSession(tokenHash, now)).map(MyBatisAuthMapper::account);
    }

    @Override
    public void deleteSession(String tokenHash) {
        sql.deleteSession(tokenHash);
    }

    @Override
    public int deleteExpiredSessions(Instant now) {
        return sql.deleteExpiredSessions(now);
    }

    private static Account account(AccountRow row) {
        return new Account(row.getId(), row.getEmail(), row.getPhone(), row.getPasswordHash(), row.getRole(),
                row.getAvatarPath(), row.getStatus(), row.getCreatedAt());
    }
}

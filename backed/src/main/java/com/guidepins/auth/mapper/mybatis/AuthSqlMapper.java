package com.guidepins.auth.mapper.mybatis;

import com.guidepins.auth.mapper.entity.AccountRow;
import java.time.Instant;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 账号与会话 SQL 位于 mapper/auth/AuthSqlMapper.xml。 */
@Mapper
public interface AuthSqlMapper {
    AccountRow selectByIdentifier(@Param("email") String email, @Param("phone") String phone);
    AccountRow insertAccount(@Param("email") String email, @Param("phone") String phone,
                             @Param("passwordHash") String passwordHash);
    int insertSession(@Param("tokenHash") String tokenHash, @Param("userId") UUID userId,
                      @Param("createdAt") Instant createdAt, @Param("expiresAt") Instant expiresAt);
    AccountRow selectActiveSession(@Param("tokenHash") String tokenHash, @Param("now") Instant now);
    int deleteSession(@Param("tokenHash") String tokenHash);
    int deleteExpiredSessions(@Param("now") Instant now);
}

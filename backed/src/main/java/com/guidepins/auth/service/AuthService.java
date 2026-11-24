package com.guidepins.auth.service;

import com.guidepins.auth.domain.Account;
import com.guidepins.auth.domain.AccountIdentity;
import com.guidepins.auth.mapper.AuthDataMapper;
import com.guidepins.exception.ApiException;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 注册、密码核验与七天固定会话；原始令牌只交给 Cookie，不落库或写日志。 */
@Service
public class AuthService {
    public static final Duration SESSION_LIFETIME = Duration.ofDays(7);

    @Autowired
    private AuthDataMapper accounts;
    @Autowired
    private PasswordEncoder passwords;
    @Autowired
    private Clock clock;

    private final SecureRandom random = new SecureRandom();
    private String dummyHash;

    @PostConstruct
    void prepareDummyHash() {
        // 不存在的账号也执行一次密码校验，避免跳过哈希运算形成明显的时序差异。
        dummyHash = passwords.encode(Base64.getEncoder().encodeToString(random.generateSeed(32)));
    }

    @Transactional
    public AccountIdentity register(String email, String phone, String password) {
        checkIdentifier(email, phone);
        if (password.length() < 6) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "密码需为 6 至 128 个字符");
        }
        try {
            Account account = accounts.createAccount(email, phone, passwords.encode(password));
            if (!"user".equals(account.role()) || account.status() != 1) {
                // 数据库默认值不符合约定时整笔注册回滚，避免创建异常角色的账号。
                throw new IllegalStateException("账号默认角色或状态配置不正确");
            }
            return account.identity();
        } catch (DuplicateKeyException error) {
            throw new ApiException(HttpStatus.CONFLICT, "该登录标识已注册");
        }
    }

    @Transactional
    public LoginResult login(String email, String phone, String password) {
        checkIdentifier(email, phone);
        Optional<Account> account = accounts.findByIdentifier(email, phone);
        boolean matches = passwords.matches(password, account.map(Account::passwordHash).orElse(dummyHash));
        if (!matches || account.isEmpty() || account.get().status() != 1) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "登录标识或密码错误，或账号不可用");
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant createdAt = clock.instant();
        accounts.createSession(hash(token), account.get().id(), createdAt, createdAt.plus(SESSION_LIFETIME));
        return new LoginResult(account.get().identity(), token);
    }

    @Transactional(readOnly = true)
    public Optional<AccountIdentity> authenticate(String token) {
        if (!validToken(token)) {
            return Optional.empty();
        }
        return accounts.findActiveSession(hash(token), clock.instant()).map(Account::identity);
    }

    @Transactional
    public void logout(String token) {
        if (validToken(token)) {
            accounts.deleteSession(hash(token));
        }
    }

    @Scheduled(fixedDelayString = "${guidepins.auth.session-cleanup-delay-ms:3600000}",
            initialDelayString = "${guidepins.auth.session-cleanup-delay-ms:3600000}")
    @Transactional
    public void cleanExpiredSessions() {
        accounts.deleteExpiredSessions(clock.instant());
    }

    private void checkIdentifier(String email, String phone) {
        if ((email == null) == (phone == null)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "请只填写邮箱或含国家码的手机号中的一项");
        }
    }

    private boolean validToken(String token) {
        return token != null && token.matches("[A-Za-z0-9_-]{43}");
    }

    private String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("运行环境缺少 SHA-256", error);
        }
    }

    public record LoginResult(AccountIdentity user, String token) {
        @Override
        public String toString() {
            return "LoginResult[userId=" + user.id() + ", token=已隐藏]";
        }
    }
}

package com.guidepins.auth.mapper.entity;

import java.time.Instant;
import java.util.UUID;
import lombok.Data;

/** user_account 数据库行；只在认证模块内部转换，不由接口直接返回。 */
@Data
public class AccountRow {
    /** 账号 UUID。 */
    private UUID id;
    /** 规范化邮箱登录标识。 */
    private String email;
    /** 含国家码的手机号登录标识。 */
    private String phone;
    /** Argon2id 完整密码编码。 */
    private String passwordHash;
    /** 产品角色：user 或 admin。 */
    private String role;
    /** 可空的头像文件相对路径。 */
    private String avatarPath;
    /** 账号状态：1 正常，0 禁用。 */
    private short status;
    /** 账号创建时间点。 */
    private Instant createdAt;

    @Override
    public String toString() {
        return "AccountRow[id=" + id + "]";
    }
}

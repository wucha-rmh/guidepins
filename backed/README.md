# 拾点地图后端

Spring Boot / Java 25 后端，使用 MyBatis XML、MyBatis-Plus 和 PostgreSQL。提供地市与底图、公共地图与图钉读取，以及阶段二的注册登录、数据库会话和私人地图管理。

## 模块

| 模块 | 职责 |
| --- | --- |
| `auth` | 注册、密码登录、会话、CSRF 与当前请求身份 |
| `city` | 地市目录和底图描述 |
| `publicmap` | 公开列表、地图详情、我的地图及地图元数据管理 |
| `basemap` | 南京 PMTiles 文件，支持 HTTP Range / 206 |
| `config` | 统一的安全配置、Bean 装配和 PostgreSQL UUID 类型处理 |
| `common` | 统一 JSON 响应契约与过滤器／容器错误输出等公共代码 |

各功能模块按 controller、service、mapper、domain 组织；数据库实体在 mapper.entity，SQL 接口在 mapper.mybatis，适配实现在 mapper.impl，SQL 在 resources/mapper。组件依赖使用 `@Autowired`。

## 本地运行

需要 Java 25、Maven 和可访问的 `guidepins` PostgreSQL 连接。先核对账号头像／状态增量，并按[数据库接入说明](docs/阶段二数据库接入.md)配置专用应用账号；原 `guidepins_agent` 仅有读取权限，不能运行新增写接口。

从 `backed` 目录执行：

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
# 保留已有配置，填写应用账号的真实密码；本地 HTTP 设置 GUIDEPINS_COOKIE_SECURE=false。
mvn spring-boot:run
```

`.env` 使用 Java Properties 格式，已被 Git 忽略。不要提交真实密码。默认端口 8080，可通过 `GUIDEPINS_SERVER_PORT` 修改；数据库地址、用户、密码通过 `GUIDEPINS_DB_URL`、`GUIDEPINS_DB_USER`、`GUIDEPINS_DB_PASSWORD` 指定。

从仓库根目录或 `backed` 目录启动均支持读取对应的 `.env` / `backed/.env`；若两者都存在，以后加载的 `backed/.env` 为准。底图默认相对路径按 `backed` 目录启动配置，其他工作目录使用 `GUIDEPINS_NANJING_PM` 指定绝对路径。

正式环境使用 HTTPS 同源代理，并保持 `GUIDEPINS_COOKIE_SECURE=true`。前端写请求先获取 CSRF 令牌；注册、登录、退出同样校验。

JSON 接口统一返回 `{code, message, data}`，`code` 与 HTTP 状态码一致，失败时 `data` 为 `null`；查询、身份、CSRF 令牌等内容均从 `data` 读取。退出成功返回 200 与“退出成功”。PMTiles 仍使用二进制及 Range / 206。

## 接口与验收

- [阶段二接口与接入说明](docs/阶段二接口与接入说明.md)：参数、响应、Cookie、CSRF、权限规则及前端调用例子。
- [阶段二数据库接入](docs/阶段二数据库接入.md)：DBX 最小授权 SQL 与执行顺序。
- [阶段二后端验收记录](docs/阶段二后端验收记录.md)：本轮实际验证与待验收条件。
- [项目进度](../docs/项目进度.md)：分阶段状态，用户确认前不标记验收完成。

```powershell
mvn verify
# 完整集中验证：独立本机 PostgreSQL + 最小授权账号 + Maven。
.\tools\verify-stage-two.ps1
```

普通 Maven 验证会运行模块测试；未设置隔离测试库地址时，PostgreSQL 集成测试明确跳过。完整脚本需要 PostgreSQL 的 `initdb`、`pg_ctl`、`psql` 位于 PATH 中，不连接或修改远程业务库。

## 当前范围

地图默认私人，公开地图可供访客读取，私人地图仅所有者可读；管理员可修改他人公开地图名称，不能访问他人私人地图或替他人切换公开状态。注册固定普通用户，暂不验证邮箱／手机号所有权，不提供找回密码、头像上传或账号管理页面。

图钉保持只读；图钉编辑、攻略出处和离线下载留在后续阶段。真实业务库的字段迁移、运行账号授权及联调须按接入说明完成。

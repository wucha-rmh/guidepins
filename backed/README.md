# 阶段一后端：地市与公共地图

Spring Boot 只读服务从 PostgreSQL 读取地市、公共地图和图钉，并提供南京 PMTiles 底图。阶段进展见[项目进度](../docs/项目进度.md)。

## 功能与模块

| 模块 | 职责 |
| --- | --- |
| city | 提供地市目录和底图地址 |
| publicmap | 查询地市下的公共地图及图钉 |
| basemap | 提供南京地图包，支持 HTTP Range |

模块按 controller、service、mapper、domain 分层。业务数据在 domain，数据库表映射类在 mapper.entity，接口实现在 mapper.impl；MyBatis SQL 接口及 XML 分别位于 mapper.mybatis 和 [resources/mapper](src/main/resources/mapper/)。数据库使用 MyBatis XML 查询、MyBatis-Plus 实体映射和 PostgreSQL UUID 类型处理。

## 接口

| 请求 | 内容 |
| --- | --- |
| GET /api/cities | 地市的 id、code、name、basemapPath、bounds；无数据时返回空数组 |
| GET /api/cities/{cityId}/maps | 指定地市的公共地图列表；地市不存在返回 404 |
| GET /api/maps/{mapId} | 地图详情及图钉名称、说明和坐标；地图不存在返回 404 |
| GET /maps/nanjing/v1/nanjing-z16.pmtiles | 南京底图；Range 请求返回 206，文件不存在返回 404 |

ID 使用 UUID，坐标使用 WGS84（经度在前）。前端从地市接口取得 basemapPath，再请求对应底图地址。

## 本地运行

需要 Java 25、Maven、已开启的 FinalShell 数据库端口转发，以及按[阶段一数据库文档](../docs/技术/阶段一数据库建库建表.md)准备的数据。从 backed/ 目录运行：

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
# 在 .env 中填写 GUIDEPINS_DB_PASSWORD
mvn spring-boot:run
```

.env 按 Java Properties 格式填写，已被 Git 忽略；.env.example 是可提交的示例，不保存真实密码。后端默认监听 8080，可在 .env 中设置 GUIDEPINS_SERVER_PORT 修改。默认数据库地址为 127.0.0.1:5433/guidepins；可用 GUIDEPINS_DB_URL、GUIDEPINS_DB_USER、GUIDEPINS_DB_PASSWORD 覆盖。地图包默认读取 ../map/南京全市离线地图/nanjing-z16.pmtiles；从其他目录启动时可用 GUIDEPINS_NANJING_PM 指定绝对路径。

## 验证与阶段限制

```powershell
mvn clean verify
curl.exe http://localhost:8080/api/cities
curl.exe http://localhost:8080/api/cities/32010000-0000-4000-8000-000000000001/maps
curl.exe http://localhost:8080/api/maps/32010000-0000-4000-8000-000000000002
curl.exe -D - -o NUL -H 'Range: bytes=0-15' http://localhost:8080/maps/nanjing/v1/nanjing-z16.pmtiles
```

现有测试按模块组织为四组单元测试，不连接远程数据库；真实数据库和 Range 联调结果见[项目进度](../docs/项目进度.md)。当前表中的地图均按公共地图读取；阶段二引入私人地图前，须增加可见性过滤及权限测试。

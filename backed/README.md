# 阶段一后端：地市与公共地图只读服务

本服务通过 MyBatis 与 MyBatis-Plus 读取现有 PostgreSQL `city`、`travel_map`、`map_pin` 三张表，并提供南京 PMTiles 文件。当前表中的地图均按站点公共地图处理。本阶段没有写接口。

## 已实现功能

1. 访客获取已接入的地市目录，包括地市编号、名称、底图 URL 和初始浏览范围。
2. 按地市读取站点公共地图列表，打开地图时读取其图钉、坐标和说明；不存在的地市或地图返回 404。
3. 从本地南京 PMTiles 文件提供底图，支持浏览器按字节范围读取；文件缺失时返回 404。
4. 使用只读数据库连接，三个 Mapper XML 定义阶段一的全部业务查询，MyBatis-Plus 保留实体映射能力。

当前完整项目已有南京离线查看器、地图包、数据库示例数据和上述后端接口。网站前端及远程数据库联调的状态见[项目进度](../docs/项目进度.md)。

## 模块职责

| 模块 | 接口层 | 业务层 | 数据访问或文件层 |
| --- | --- | --- | --- |
| `city` | 返回地市目录 | 查询地市、判断地市是否存在 | 读取 `public.city` |
| `publicmap` | 返回地市下的地图及地图详情 | 检查地市或地图是否存在、组合图钉 | 读取 `public.travel_map`、`public.map_pin` |
| `basemap` | 返回南京地图包，支持 HTTP Range | 提供底图读取入口 | 检查并读取本地 PMTiles 文件 |

每个功能模块按需使用 `controller`、`service`、`mapper`、`domain` 包。`city.domain` 和 `publicmap.domain` 保存接口使用的业务数据记录；与数据库表对应的可变 `*Row` 实体放在各自的 `mapper.entity`，使用 Lombok `@Data` 生成访问方法。接口数据继续使用不可变的 Java `record`。`basemap` 没有数据库实体。`com.guidepins.exception` 保存三个模块共用的 `ResourceNotFoundException`，统一将资源不存在映射为 HTTP 404。各模块实现数据读取接口的类放在对应的 `mapper.impl` 包。

数据库查询 SQL 位于 `src/main/resources/mapper/city/CitySqlMapper.xml`、`mapper/publicmap/TravelMapSqlMapper.xml` 和 `mapper/publicmap/MapPinSqlMapper.xml`。各模块 `mapper` 根包是服务层使用的数据读取接口，`mapper.impl` 中的实现调用 `mapper.mybatis` 的 SQL Mapper 并将数据库实体转换为业务数据。所有过滤条件使用 `#{}` 绑定 PostgreSQL UUID 参数，XML 中明确列映射和稳定排序。SQL Mapper 继承 MyBatis-Plus `BaseMapper` 以保留实体映射能力，但当前业务读取均走 XML。Boot 3 Starter 已包含所需 MyBatis 依赖，项目不再单独引入 MyBatis Starter；依赖选择依据见 [MyBatis-Plus 安装文档](https://baomidou.com/getting-started/install/)。

## 本地配置与 PostgreSQL 映射

`application.yml` 在从 `backed/` 启动时读取不提交的 `.env`，文件按 Java Properties 格式填写。仓库中的 `.env.example` 仅提供键名和示例值。系统环境变量也可以覆盖本机文件的对应配置。

当前配置明确使用 PostgreSQL JDBC 驱动和 `public` schema；MyBatis-Plus 把下划线列名映射到 Java 驼峰字段，UUID 类型处理器以 PostgreSQL 原生 `uuid` 类型绑定参数并读取结果。主键由应用提供，空值使用 JDBC `NULL` 类型。MyBatis 没有单一的“PostgreSQL 模式”开关；当前阶段没有分页接口，因此没有引入分页方言插件。

`publicmap.service` 通过 `CityService` 查询地市是否存在。前端取得 `basemapPath` 后，直接向同一服务请求地图包。

## 接口

| 请求 | 返回 | 不存在时 |
| --- | --- | --- |
| `GET /api/cities` | 地市数组：`id`、`code`、`name`、`basemapPath`、`bounds`（`west`、`south`、`east`、`north`） | 空数组 |
| `GET /api/cities/{cityId}/maps` | 该地市公共地图数组：`id`、`cityId`、`name` | 地市不存在返回 404 |
| `GET /api/maps/{mapId}` | 地图的 `id`、`cityId`、`name` 和 `pins`；图钉含 `id`、`name`、`description`、`longitude`、`latitude` | 地图不存在返回 404 |
| `GET /maps/nanjing/v1/nanjing-z16.pmtiles` | 地图包二进制；带 `Range: bytes=...` 时返回 206 和 `Content-Range` | 文件不存在返回 404 |

所有坐标采用 WGS84，经度在前。API 中的编号是 UUID。接口只提供 GET，且默认数据库账号仅有 `SELECT` 权限。

## 本机运行

需要 Java 25、Maven 和已开启的 FinalShell 端口转发。数据库结构和南京数据按[阶段一数据库文档](../docs/技术/阶段一数据库建库建表.md)准备。首次运行时，将 `.env.example` 复制为 `.env` 并填写密码。从 `backed/` 目录运行：

```powershell
mvn spring-boot:run
```

默认连接 `jdbc:postgresql://127.0.0.1:5433/guidepins`，账号为 `guidepins_agent`。可通过 `GUIDEPINS_DB_URL`、`GUIDEPINS_DB_USER`、`GUIDEPINS_DB_PASSWORD` 覆盖连接信息。地图包默认从相对 `backed/` 的 `../map/南京全市离线地图/nanjing-z16.pmtiles` 读取；从其他目录启动时，使用 `GUIDEPINS_NANJING_PM` 指定其绝对路径。数据库中南京的 `basemap_path` 应为 `/maps/nanjing/v1/nanjing-z16.pmtiles`。

启动后检查：

```powershell
curl.exe http://localhost:8080/api/cities
curl.exe http://localhost:8080/api/cities/32010000-0000-4000-8000-000000000001/maps
curl.exe http://localhost:8080/api/maps/32010000-0000-4000-8000-000000000002
curl.exe -D - -o NUL -H 'Range: bytes=0-15' http://localhost:8080/maps/nanjing/v1/nanjing-z16.pmtiles
```

最后一个请求应返回 `206 Partial Content` 和 `Content-Range: bytes 0-15/28281061`。地图包大小以本地文件为准。

## 测试

```powershell
mvn test
```

测试按功能模块组织为 `CityModuleTest`、`PublicMapModuleTest`、`BasemapModuleTest` 和 PostgreSQL 配置模块的 `PostgresPersistenceModuleTest`，使用模拟 Mapper 检查模块行为、XML Mapper 方法调用与 UUID 处理。底图模块使用临时文件检查读取与 Range 响应。测试均为本地单元测试，不连接远程数据库；XML SQL 和实际字段映射另由真实 PostgreSQL 接口请求验证。

2026-11-23 在开启 FinalShell 端口转发后，使用本机 `.env` 启动 JAR，实际读取到了南京、南京秦淮示例地图和 3 个图钉；底图 Range 请求返回 `206`。本次包结构整理后，集中执行 `mvn clean verify`，四组 10 项单元测试通过；三个业务接口再次通过真实 PostgreSQL 验证。本机密钥文件不随仓库交付，其他开发环境需自行填写。

阶段二加入私人地图前，必须为公共地图查询增加可见性过滤和相应权限测试，防止私人地图经本阶段的只读接口暴露。

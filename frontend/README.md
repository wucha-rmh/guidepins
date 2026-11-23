# 拾点地图 · PC 前端

当前完成阶段一的第 2 小步：**真实地市目录、南京 PMTiles 底图、公共地图列表与图钉详情**。访客可以从地市目录进入南京，打开一张公共地图，在地图上看到真实图钉并查看说明；列表和详情都有独立的加载、失败与重试状态。阶段一访客主流程已可整体验收。

底图样式对齐 `map/南京全市离线地图/nanjing-city-map-v2.html` 的 V2「城区细节版」：进入地市先落在主城区街区，建筑画在道路之下并随缩放加深，道路按等级分色，标注分为地名、道路名、建筑名三级，市域以外有遮罩并描出市界。

## 本地运行

需要 Node.js 22.12 及以上版本。先在 FinalShell 开启既有数据库端口转发，再按 [后端说明](../backed/README.md) 启动后端。在两个 PowerShell 终端分别执行：

后端终端：

```powershell
cd D:\GuidePins\backed
mvn spring-boot:run
```

前端终端：

```powershell
cd D:\GuidePins\frontend
npm ci
npm run dev
```

默认访问 http://127.0.0.1:5173/。地市、公共地图请求都通过同源代理访问后端，不需要新增后端跨域设置。默认后端地址是 http://127.0.0.1:8080；如实际端口不同，将 .env.example 复制为 .env，修改 GUIDEPINS_BACKEND_URL，再重新启动前端。前端 .env 不需要数据库账号或密码。

```powershell
# 可选：按需建立本地代理配置，不覆盖已有配置
if (-not (Test-Path .env)) { Copy-Item .env.example .env }

# 严格类型检查
npm run typecheck

# 类型检查并构建到 dist
npm run build

# 预览已有构建，默认 http://127.0.0.1:4173/
npm run preview
```

dev 和 preview 均代理 /api 与 /maps，保持路径和 Range / 206 响应不变。生产部署时应由站点服务器提供同源的 /api、/maps 转发；Vite 本地代理配置不会写入构建产物。

端口占用时可用 npm run dev -- --port 5174。只实现 PC 布局，最小宽度为 1100px。

## 模块与职责

| 位置 | 职责 |
| --- | --- |
| src/app | 应用布局、Hash 路由、页面组合；地图页面按需加载 |
| src/app/MapPage.vue | 组合地市、公共地图列表与详情，持有当前选中的图钉，处理未知地市与未知地图 |
| src/app/styles.css | 页面布局、公共地图卡片、图钉列表与地图气泡等样式 |
| src/modules/city/types.ts | 地市和 WGS84 边界契约 |
| src/modules/city/api.ts | GET /api/cities 请求、HTTP 状态和响应格式校验 |
| src/modules/city/useCities.ts | 地市目录加载、15 秒超时、取消旧请求与重试 |
| src/modules/city/CitySelectionPage.vue | 渲染真实地市入口和加载、空目录、错误状态 |
| src/modules/public-map/types.ts | 公共地图、图钉、地图详情的契约；坐标为 WGS84，经度在前 |
| src/modules/public-map/api.ts | GET /api/cities/{cityId}/maps 与 GET /api/maps/{mapId}，校验响应格式并把 404、400 与其他失败区分开 |
| src/modules/public-map/useRemoteResource.ts | 只读资源的加载、15 秒超时、取消旧请求、区分“不存在”与“请求失败”、重试与重置 |
| src/modules/public-map/usePublicMaps.ts | 按 URL 中的地市 id 加载公共地图列表，地市变化时重取 |
| src/modules/public-map/usePublicMapDetail.ts | 按 URL 中的地图 id 加载地图详情与图钉，地图变化时重取 |
| src/modules/public-map/PublicMapPanel.vue | 公共地图列表、当前地图标记、空列表、失败与重试 |
| src/modules/public-map/MapDetailPanel.vue | 地图名与图钉数、图钉条目与选中态、暂未图钉的提示 |
| src/modules/basemap/style.ts | V2 底图样式：配色、水域、建筑渐显、道路分层、三级标注与市界遮罩图层 |
| src/modules/basemap/views.ts | 各城市的主城区观察点与回退规则 |
| src/modules/basemap/createBasemap.ts | MapLibre 实例、PMTiles 协议、Worker、控件、超时、市界数据、图钉图层挂载与资源释放 |
| src/modules/basemap/pinLayers.ts | 图钉点、光晕与选中环，点击命中判断，图钉说明气泡 |
| src/modules/basemap/BasemapViewport.vue | 地图容器、主城区与查看全市按钮、图钉数据与选中状态的视图联动 |
| src/shared/StatePanel.vue | 各模块复用的加载、空数据、失败提示与重试结构 |

没有引入全局 store 或新的通用请求框架。地图选择以 URL 为唯一来源，页面只持有一份内存中的图钉选中状态。

## 数据与地图行为

- 地市 id、code、name、basemapPath、bounds 全部取自 GET /api/cities，不写死南京 ID、地址或中心点。
- 公共地图、地图详情与图钉全部取自接口，不写死地图 ID、图钉坐标或文案；图钉没有说明时不显示说明行。
- bounds 按 [[west, south], [east, north]] 描述行政区范围；“查看全市”适配该范围，“主城区”回到城市默认观察点。
- 进入地市先显示主城区首屏（views.ts 中按城市 code 配置，南京取新街口一带、级别 14.2）；没有配置的地市退回行政区范围中心、级别 13。
- PMTiles 按 HTTP Range 读取，支持拖动、滚轮、按钮和键盘缩放，最大视图级别为 20；建筑从级别 14 开始显示，建筑名从级别 15、道路名从级别 13 开始显示。
- 打开公共地图后视野自动收到图钉范围（留白 96px，最大级别 16）；点击侧栏条目会飞到该图钉（级别至少 16）；点击地图上的图钉只切换选中并显示气泡，不改变视野；点击空白处清除选中并关闭气泡。
- 图钉图层只有圆点、光晕和选中环，不添加文字标注：底图样式没有字形服务，文字由说明气泡和侧栏条目承担。标注缺字形时只丢那一行文字，不把整幅底图判为失败。
- 列表与详情各自 15 秒超时。404（以及地图 id 非法时的 400）表示资源不存在，提示后给出返回入口，不做无意义重试；其他失败和超时给出重试按钮，重试会重新发出真实请求。
- 地图详情返回的 cityId 与 URL 中的地市不一致时按“找不到这张地图”处理，避免把别的地市的地图显示在当前城市下。
- 样式取 V2「城区细节版」：建筑画在道路之前并随级别提高不透明度，主干道与次干道分色，地名按城市、街道、博物馆分级。文字使用本机字体，不依赖外部字形服务；保留 OpenStreetMap 数据署名。
- 市界遮罩与市界描边用 `map/南京全市离线地图/nanjing-boundary.js` 转成的 GeoJSON 资源（src/modules/basemap/nanjing-boundary.json，523 KB，随构建输出为独立文件），底图就绪后异步补进 cityLimit 数据源；取不到只丢遮罩，不影响底图。
- 地图 Worker 作为独立构建资源交由 Vite 输出；容器变化时同步调整画布尺寸。
- 底图请求持续 20 秒未进入空闲状态时显示超时；加载错误或 WebGL 中断时显示失败及重试入口。
- 重试销毁旧地图并创建新的协议与缓存，重新请求地图包；地市、公共地图与详情接口重试重新发出真实请求。
- 页面离开时取消接口请求、释放地图实例、尺寸监听与定时器。

## 路由与刷新

| 路径 | 内容 |
| --- | --- |
| /#/ | 真实地市目录 |
| /#/cities/:cityId | 目录中对应地市的真实底图与公共地图列表 |
| /#/cities/:cityId/maps/:mapId | 该地市底图、公共地图列表、地图详情与图钉 |
| 未知地市或页面 | 不存在提示与返回入口 |

实际南京地址以首页点击结果为准，本次数据库返回的 cityId 为 32010000-0000-4000-8000-000000000001，公共地图 id 为 32010000-0000-4000-8000-000000000002。旧骨架地址 preview-nanjing 与 preview-qinhuai 已移除。

直接打开或刷新地图页时，按 URL 中的地市 id 与地图 id 重新取得目录、列表与详情，恢复底图和图钉；不恢复上次选中的图钉，也不保存用户最后拖动的位置和缩放级别。MapLibre 不写入 URL Hash，避免覆盖页面路由。

## 本轮验收

1. 开启数据库隧道及后端，首页读取真实目录，可选择南京。
2. 进入地市先看到主城区街区（级别 14.2，有建筑与道路名）；“查看全市”看到完整市域与市界轮廓，“主城区”回到街区首屏。
3. 左侧列出真实公共地图；打开后地图上出现真实图钉，视野收到图钉范围。
4. 点击侧栏图钉条目，地图飞到该图钉并显示名称与说明气泡；直接点击地图上的图钉也能选中；点击空白处取消选中。
5. 刷新、直接打开地图地址、前进后退、返回地市选择、关闭公共地图均正常，关闭后图钉与气泡消失。
6. 公共地图列表失败、空列表、超时都有明确提示；详情失败、超时与“暂无图钉”同样有对应提示与重试。
7. 未知地市、未知地图与非法地图 id 显示“找不到”提示及返回入口，不出现白屏或串地市。
8. 底图请求失败或超时有明确提示、重试及顶部返回入口；恢复服务后重试可重新绘制。

实际验证结果与截图见 [底图样式 V2 验收记录](底图样式V2验收记录.md) 与 [公共地图与图钉验收记录](公共地图与图钉验收记录.md)。上一小步见 [地市与底图验收记录](地市与底图验收记录.md)，[原骨架验收记录](验收记录.md)保留为历史记录。

"""在独立本机 PostgreSQL、真实 JAR 和生产预览上验证南京下载与双击离线打开。

先在 frontend 执行 npm run build，再在 backed 执行 mvn verify；需要 Python playwright、
PostgreSQL 工具和已安装的 Edge、Chrome。不会读取 .env 或连接业务数据库。
"""
from pathlib import Path
import base64
import gzip
import json
import os
import shutil
import socket
import struct
import subprocess
import time
import urllib.request
import uuid
from playwright.sync_api import sync_playwright, expect

ROOT = Path(__file__).resolve().parents[2]
RESULT = ROOT / "backed" / "target" / ("offline-browser-" + uuid.uuid4().hex[:10])
RESULT.mkdir(parents=True)
FLAGS = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
CITY = "32010000-0000-4000-8000-000000000001"
BASEMAP = ROOT / "map/江苏省地图数据/城市"
SOURCE = next((BASEMAP / "320100-南京市").glob("*.pmtiles"))


def port():
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", 0))
        return probe.getsockname()[1]


def run(command, name):
    with (RESULT / name).open("w", encoding="utf-8") as log:
        subprocess.run(command, cwd=ROOT, stdout=log, stderr=log, check=True,
                       timeout=60, creationflags=FLAGS)


def wait_ready(url, process):
    for _ in range(150):
        if process.poll() is not None:
            raise RuntimeError(f"测试服务退出：{process.returncode}，详见 {RESULT}")
        try:
            with urllib.request.urlopen(url, timeout=1) as response:
                if response.status == 200:
                    return
        except Exception:
            time.sleep(0.2)
    raise RuntimeError(f"服务启动超时：{url}")


db_port, api_port, web_port = port(), port(), port()
processes, logs = [], []
started = False
try:
    print(f"验证目录：{RESULT}", flush=True)
    run(["initdb", "-D", str(RESULT / "pg"), "-A", "trust", "-U", "guidepins_test_owner",
         "--no-locale", "--encoding=UTF8"], "initdb.log")
    run(["pg_ctl", "-D", str(RESULT / "pg"), "-l", str(RESULT / "postgres.log"),
         "-o", f"-h 127.0.0.1 -p {db_port}", "-w", "start"], "start.log")
    started = True
    psql = ["psql", "-X", "-v", "ON_ERROR_STOP=1", "-h", "127.0.0.1", "-p", str(db_port),
            "-U", "guidepins_test_owner"]
    run(psql + ["-d", "postgres", "-c", "CREATE DATABASE guidepins_backend_test"], "database.log")
    run(psql + ["-d", "guidepins_backend_test", "-f", str(ROOT / "backed/src/test/resources/postgres/schema.sql")], "schema.log")
    with SOURCE.open("rb") as file:
        header = file.read(127)
    west, south, east, north = [value / 1e7 for value in struct.unpack_from("<iiii", header, 102)]
    seed = RESULT / "seed.sql"
    seed.write_text(f"INSERT INTO city(id,code,name,basemap_path,west,south,east,north) VALUES ('{CITY}','320100','南京','/maps/320100.pmtiles',{west},{south},{east},{north});", encoding="utf-8")
    run(psql + ["-d", "guidepins_backend_test", "-f", str(seed)], "seed.log")
    env = dict(os.environ, GUIDEPINS_DB_URL=f"jdbc:postgresql://127.0.0.1:{db_port}/guidepins_backend_test",
               GUIDEPINS_DB_USER="guidepins_app", GUIDEPINS_DB_PASSWORD="",
               GUIDEPINS_COOKIE_SECURE="false", GUIDEPINS_SERVER_PORT=str(api_port),
               GUIDEPINS_BASEMAP_ROOT=str(BASEMAP), GUIDEPINS_BACKEND_URL=f"http://127.0.0.1:{api_port}")
    api_log = (RESULT / "backend.log").open("w", encoding="utf-8"); logs.append(api_log)
    api = subprocess.Popen(["java", "-jar", str(ROOT / "backed/target/guidepins-backend-0.1.0-SNAPSHOT.jar"),
                            "--spring.config.import="], cwd=ROOT, env=env, stdout=api_log, stderr=api_log, creationflags=FLAGS)
    processes.append(api)
    wait_ready(f"http://127.0.0.1:{api_port}/api/cities", api)
    # 生产预览也禁用 dotenv；代理只指向本轮临时后端，避免使用开发者本地连接配置。
    preview_config = RESULT / "preview.config.mjs"
    preview_config.write_text("export default " + json.dumps({"envDir": False, "preview": {"proxy": {
        "/api": {"target": f"http://127.0.0.1:{api_port}", "changeOrigin": True},
        "/maps": {"target": f"http://127.0.0.1:{api_port}", "changeOrigin": True}}}}), encoding="utf-8")
    web_log = (RESULT / "frontend.log").open("w", encoding="utf-8"); logs.append(web_log)
    web = subprocess.Popen([shutil.which("node"), str(ROOT / "frontend/node_modules/vite/bin/vite.js"),
                            "preview", "--config", str(preview_config), "--host", "127.0.0.1", "--port", str(web_port), "--strictPort"],
                           cwd=ROOT / "frontend", env=env, stdout=web_log, stderr=web_log, creationflags=FLAGS)
    processes.append(web)
    origin = f"http://127.0.0.1:{web_port}"
    wait_ready(origin, web)
    summary = {"sourceBytes": SOURCE.stat().st_size, "browsers": {}}
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch(channel="msedge", headless=True)
        context = browser.new_context(accept_downloads=True, viewport={"width": 1440, "height": 900})
        request = context.request

        def write(method, path, data):
            csrf = request.get(origin + "/api/auth/csrf").json()["data"]
            response = request.fetch(origin + path, method=method, data=data,
                                     headers={csrf["headerName"]: csrf["token"]})
            assert response.ok, (response.status, response.text())
            return response.json()["data"]

        account = {"email": "offline-browser@example.test", "password": "GuidePins-Test-123!"}
        write("POST", "/api/auth/register", account); write("POST", "/api/auth/login", account)
        travel = write("POST", f"/api/cities/{CITY}/maps", {"name": "南京三处慢慢逛", "visibility": "public"})
        map_id = travel["id"]
        points = []
        for name, lon, lat in [("夫子庙", 118.79, 32.02), ("新街口", 118.79, 32.045), ("玄武湖", 118.8, 32.08)]:
            points.append(write("POST", f"/api/maps/{map_id}/pins", {"name": name, "description": "沿途说明 <script>window.injected=true</script>", "longitude": lon, "latitude": lat}))
        guide = write("POST", f"/api/maps/{map_id}/guides", {"title": "南京半日", "body": "先到玄武湖，再去夫子庙。\n正文 </script><script>window.injected=true</script>", "sourceUrl": "https://example.test/source"})
        write("PUT", f"/api/maps/{map_id}/guides/{guide['id']}/pins", {"pinIds": [points[2]["id"], points[0]["id"], points[1]["id"]]})
        page = context.new_page()
        online_errors = []
        page.on("pageerror", lambda error: online_errors.append(str(error)))
        page.goto(f"{origin}/#/cities/{CITY}/maps/{map_id}")
        expect(page.get_by_role("button", name="主城区", exact=True)).to_be_enabled(timeout=30000)
        page.get_by_role("button", name="主城区", exact=True).click()
        page.wait_for_timeout(700)
        page.wait_for_load_state("networkidle")
        page.screenshot(path=str(RESULT / "地铁-网站街区.png"))
        for _ in range(2):
            page.locator(".maplibregl-ctrl-zoom-out").click(); page.wait_for_timeout(450); page.wait_for_load_state("networkidle")
        expect(page.locator(".map-zoom-label")).to_have_text("城市视角")
        page.screenshot(path=str(RESULT / "地铁-网站城市.png"))
        for _ in range(4):
            page.locator(".maplibregl-ctrl-zoom-in").click(); page.wait_for_timeout(450); page.wait_for_load_state("networkidle")
        expect(page.locator(".map-zoom-label")).to_have_text("街区视角")
        page.screenshot(path=str(RESULT / "地铁-网站近景.png"))
        assert not online_errors, online_errors
        expect(page.locator(".basemap-state")).to_have_count(0)
        button = page.get_by_role("button", name="下载离线地图", exact=True)
        expect(button).to_be_visible(timeout=30000)
        button.scroll_into_view_if_needed()
        calls = []
        page.route("**/offline", lambda route: (calls.append(route.request.url), route.continue_()))
        with page.expect_download(timeout=120000) as downloading:
            button.evaluate("button => { button.click(); button.click(); }")
        downloaded = downloading.value
        sample = RESULT / "南京三处慢慢逛.html"
        downloaded.save_as(sample)
        assert len(calls) == 1, calls
        expect(page.get_by_role("status").filter(has_text="文件已交给浏览器保存")).to_be_visible()
        page.screenshot(path=str(RESULT / "网站下载入口.png"), full_page=True)
        summary["fileBytes"] = sample.stat().st_size
        summary["reductionPercent"] = round((1 - sample.stat().st_size / SOURCE.stat().st_size) * 100, 1)
        page.unroute("**/offline")
        page.route("**/offline", lambda route: route.fulfill(status=503, content_type="application/json",
                    body=json.dumps({"code": 503, "message": "测试生成失败，请稍后重试", "data": None})))
        button.click(); expect(page.get_by_role("alert").filter(has_text="测试生成失败")).to_be_visible()
        empty = write("POST", f"/api/cities/{CITY}/maps", {"name": "空地图", "visibility": "private"})
        empty_response = request.get(origin + f"/api/maps/{empty['id']}/offline", timeout=120000)
        assert empty_response.ok
        empty_file = RESULT / "南京空地图.html"; empty_file.write_bytes(empty_response.body())
        summary["emptyFileBytes"] = empty_file.stat().st_size
        # 私人下载被延迟时，关图或退出不能保存迟到的文件。
        page.unroute("**/offline")
        late_downloads, held = [], []
        page.on("download", lambda download: late_downloads.append(download.suggested_filename))
        page.route("**/offline", lambda route: held.append(route))
        for action in ("close", "logout"):
            page.goto(f"{origin}/#/cities/{CITY}/maps/{empty['id']}")
            page.get_by_role("button", name="下载离线地图", exact=True).click()
            expect(page.get_by_role("button", name="正在生成并下载…", exact=True)).to_be_disabled()
            assert held
            if action == "close":
                page.locator(".close-map").click()
                expect(page.get_by_role("button", name="下载离线地图", exact=True)).to_have_count(0)
            else:
                page.get_by_role("button", name="个人中心", exact=True).click()
                page.get_by_role("button", name="退出登录", exact=True).click()
                expect(page.get_by_role("button", name="下载离线地图", exact=True)).to_have_count(0)
            held.pop(0).fulfill(status=200, content_type="text/html", body=empty_response.body())
            page.wait_for_timeout(600)
            assert not late_downloads, late_downloads
        summary["website"] = {"download": True, "duplicateClickSuppressed": True,
                              "errorMessage": True, "cancelOnMapClose": True, "cancelOnLogout": True}
        browser.close()
        print(f"网站下载通过，三点样包 {summary['fileBytes']:,} 字节，减少 {summary['reductionPercent']}%", flush=True)

        for channel in ("msedge", "chrome"):
            browser = playwright.chromium.launch(channel=channel, headless=True)
            context = browser.new_context(offline=True, viewport={"width": 1440, "height": 900})
            offline = context.new_page(); errors, external = [], []
            offline.on("pageerror", lambda error: errors.append(str(error)))
            offline.on("console", lambda message: errors.append(message.text) if message.type == "error" else None)
            offline.on("request", lambda request: external.append(request.url) if request.url.startswith(("http:", "https:")) else None)
            offline.goto(sample.as_uri(), wait_until="load")
            expect(offline.locator("#status")).to_contain_text("离线可用", timeout=60000)
            expect(offline.locator("#title")).to_have_text("南京三处慢慢逛")
            assert offline.locator("#pins > li").count() == 3
            offline.locator("#pins > li > button").filter(has_text="夫子庙").click()
            expect(offline.locator(".maplibregl-popup-content h3")).to_have_text("夫子庙")
            offline.locator("#pins .sources button").first.click()
            assert offline.locator("#guides details").first.get_attribute("open") is not None
            assert offline.locator(".guide-stops button").all_text_contents() == ["1. 玄武湖", "2. 夫子庙", "3. 新街口"]
            offline.get_by_role("button", name="1. 玄武湖", exact=True).click()
            expect(offline.locator(".maplibregl-popup-content h3")).to_have_text("玄武湖")
            expect(offline.locator("#map")).to_have_attribute("aria-busy", "false", timeout=60000)
            assert offline.evaluate("window.injected") is None
            offline.screenshot(path=str(RESULT / f"{channel}-离线街区.png"))
            offline.locator(".maplibregl-popup-close-button").click()
            for _ in range(2):
                offline.locator(".maplibregl-ctrl-zoom-out").click()
                expect(offline.locator("#map")).to_have_attribute("aria-busy", "false", timeout=60000)
            offline.screenshot(path=str(RESULT / f"地铁-{channel}-离线街区.png"))
            for _ in range(2):
                offline.locator(".maplibregl-ctrl-zoom-out").click()
                expect(offline.locator("#map")).to_have_attribute("aria-busy", "false", timeout=60000)
            offline.screenshot(path=str(RESULT / f"地铁-{channel}-离线城市.png"))
            offline.get_by_role("button", name="查看全市", exact=True).click()
            expect(offline.locator("#map")).to_have_attribute("aria-busy", "false", timeout=60000)
            offline.screenshot(path=str(RESULT / f"{channel}-离线全市.png"))
            offline.reload(); expect(offline.locator("#status")).to_contain_text("离线可用", timeout=60000)
            offline.goto(empty_file.as_uri()); expect(offline.locator("#status")).to_contain_text("离线可用", timeout=60000)
            expect(offline.locator("#pins")).to_contain_text("这张地图暂无地点")
            assert not errors, errors
            assert not external, external
            summary["browsers"][channel] = {"offlineOpen": True, "reload": True, "emptyMap": True,
                                             "pinGuideOrder": True, "externalRequests": external, "scriptErrors": errors}
            browser.close()
            print(f"{channel} 断网打开、缩放、图钉攻略联动和空地图通过", flush=True)
    (RESULT / "results.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(summary, ensure_ascii=False), flush=True)
finally:
    for process in reversed(processes):
        if process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                process.kill(); process.wait()
    for log in logs:
        log.close()
    if started:
        run(["pg_ctl", "-D", str(RESULT / "pg"), "-m", "fast", "-w", "stop"], "stop.log")

package com.guidepins.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * 独立 PostgreSQL 上验证阶段三攻略与出处：一篇攻略挂多个图钉、一个图钉被多篇攻略提到，
 * 以及关联收缩、删除攻略、删除图钉三种情况下出处与攻略各自的存留规则。
 */
@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.datasource.url=${GUIDEPINS_TEST_DB_URL}",
        "spring.datasource.username=guidepins_app",
        "spring.datasource.password=",
        "guidepins.auth.cookie-secure=true"
})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "GUIDEPINS_TEST_DB_URL", matches = ".+")
class StageThreeGuideIntegrationTest {
    private static final UUID CITY = UUID.fromString("32010000-0000-4000-8000-000000000001");
    private static final String PASSWORD = "GuidePins-Test-123!";
    private static final Path BASEMAP_ROOT = Path.of("target", "stage-three-guide-basemap").toAbsolutePath();

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;

    @DynamicPropertySource
    static void fileProperty(DynamicPropertyRegistry registry) throws Exception {
        // 按生产的「代码-市名」目录约定造一份最小底图，验证按代码寻址。
        Path cityDirectory = Files.createDirectories(BASEMAP_ROOT.resolve("320100-南京市"));
        Files.write(cityDirectory.resolve("nanjing-z16.pmtiles"), new byte[] {1, 2, 3, 4, 5});
        Files.writeString(cityDirectory.resolve("boundary.geojson"), "{\"type\":\"FeatureCollection\",\"features\":[]}");
        registry.add("guidepins.basemap.root", BASEMAP_ROOT::toString);
    }

    @AfterAll
    static void removeFile() throws Exception {
        Files.deleteIfExists(BASEMAP_ROOT.resolve("320100-南京市").resolve("nanjing-z16.pmtiles"));
        Files.deleteIfExists(BASEMAP_ROOT.resolve("320100-南京市").resolve("boundary.geojson"));
    }

    @BeforeEach
    void resetIsolatedDatabase() throws Exception {
        try (Connection connection = ownerConnection()) {
            var sql = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            sql.execute("TRUNCATE public.pin_guide, public.guide, public.map_pin, public.travel_map, "
                    + "public.auth_session, public.user_account, public.city");
            sql.update("INSERT INTO public.city (id, code, name, basemap_path, west, south, east, north) "
                    + "VALUES (?, '320100', '南京', '/maps/320100.pmtiles', 118.3, 31.2, 119.2, 32.6)", CITY);
        }
    }

    @Test
    void linksOneGuideToManyPinsAndOnePinToManyGuides() throws Exception {
        Client owner = registered("alice@example.test");
        UUID map = createMap(owner, "南京散步", "private");
        UUID gate = createPin(owner, map, "中华门", 118.77, 32.01);
        UUID temple = createPin(owner, map, "夫子庙", 118.79, 32.02);
        UUID bridge = createPin(owner, map, "文德桥", 118.788, 32.018);

        // 一篇攻略按站序关联三个图钉，返回的关联顺序就是请求给出的顺序。
        UUID first = createGuide(owner, map, "秦淮半日", "从中华门走到夫子庙。", "https://example.test/a");
        JsonNode ordered = body(owner.send(HttpMethod.PUT, guidePinsPath(map, first),
                pinOrder(bridge, gate, temple)).andExpect(status().isOk()).andReturn());
        assertEquals(3, ordered.get("pins").size());
        assertEquals(bridge.toString(), ordered.get("pins").get(0).get("pinId").asText());
        assertEquals(0, ordered.get("pins").get(0).get("position").asInt());
        assertEquals(temple.toString(), ordered.get("pins").get(2).get("pinId").asText());
        assertEquals(2, ordered.get("pins").get(2).get("position").asInt());

        // 三个图钉都能在详情里看到这条出处。
        for (UUID pin : List.of(gate, temple, bridge)) {
            owner.get("/api/maps/" + map + "/pins/" + pin + "/guides").andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].id").value(first.toString()))
                    .andExpect(jsonPath("$.data[0].title").value("秦淮半日"));
        }
        owner.get("/api/maps/" + map).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pins[?(@.name == '中华门')].guides[0].title").value("秦淮半日"));

        // 第二篇攻略只提到中华门：该钉两条出处，另外两钉仍各一条。
        UUID second = createGuide(owner, map, "只看城门", "中华门单点整理。", null);
        owner.send(HttpMethod.PUT, guidePinsPath(map, second), pinOrder(gate)).andExpect(status().isOk());
        owner.get("/api/maps/" + map + "/pins/" + gate + "/guides").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
        owner.get("/api/maps/" + map + "/pins/" + temple + "/guides").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        // 允许存在没有任何关联图钉的草稿攻略。
        UUID draft = createGuide(owner, map, "待补充", "还没选好地点。", null);
        owner.get(guidePath(map, draft)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pins.length()").value(0));
        owner.get(guidesPath(map)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3));
    }

    @Test
    void shrinksRelationsWithoutLosingTheGuide() throws Exception {
        Client owner = registered("alice@example.test");
        UUID map = createMap(owner, "南京散步", "private");
        UUID gate = createPin(owner, map, "中华门", 118.77, 32.01);
        UUID temple = createPin(owner, map, "夫子庙", 118.79, 32.02);
        UUID bridge = createPin(owner, map, "文德桥", 118.788, 32.018);
        UUID guide = createGuide(owner, map, "秦淮半日", "正文保持不动。", null);
        owner.send(HttpMethod.PUT, guidePinsPath(map, guide), pinOrder(gate, temple, bridge))
                .andExpect(status().isOk());

        // 关联收缩到只剩中华门：另两个钉的出处随之消失，攻略本身与正文不受影响。
        owner.send(HttpMethod.PUT, guidePinsPath(map, guide), pinOrder(gate)).andExpect(status().isOk());
        owner.get("/api/maps/" + map + "/pins/" + temple + "/guides").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        owner.get("/api/maps/" + map + "/pins/" + bridge + "/guides").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        owner.get(guidePath(map, guide)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("秦淮半日"))
                .andExpect(jsonPath("$.data.body").value("正文保持不动。"))
                .andExpect(jsonPath("$.data.pins.length()").value(1));

        // 重复提交同一组关联不算修改，地图更新时间不推进。
        Instant before = mapUpdatedAt(owner);
        owner.send(HttpMethod.PUT, guidePinsPath(map, guide), pinOrder(gate)).andExpect(status().isOk());
        assertEquals(before, mapUpdatedAt(owner));
        // 真正改变关联则推进更新时间。
        owner.send(HttpMethod.PUT, guidePinsPath(map, guide), pinOrder(gate, bridge)).andExpect(status().isOk());
        assertTrue(mapUpdatedAt(owner).isAfter(before));
    }

    @Test
    void deletesGuidesAndPinsIndependently() throws Exception {
        Client owner = registered("alice@example.test");
        UUID map = createMap(owner, "南京散步", "private");
        UUID gate = createPin(owner, map, "中华门", 118.77, 32.01);
        UUID temple = createPin(owner, map, "夫子庙", 118.79, 32.02);
        UUID first = createGuide(owner, map, "秦淮半日", "第一篇。", null);
        UUID second = createGuide(owner, map, "只看城门", "第二篇。", null);
        owner.send(HttpMethod.PUT, guidePinsPath(map, first), pinOrder(gate, temple)).andExpect(status().isOk());
        owner.send(HttpMethod.PUT, guidePinsPath(map, second), pinOrder(gate)).andExpect(status().isOk());

        // 删第二篇：中华门只剩第一篇出处，其它一切不变。
        owner.send(HttpMethod.DELETE, guidePath(map, second), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("攻略已删除"));
        owner.get("/api/maps/" + map + "/pins/" + gate + "/guides").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(first.toString()));
        owner.get(guidePath(map, second)).andExpect(status().isNotFound());

        // 删中华门图钉：攻略与另一条关联仍在。
        owner.send(HttpMethod.DELETE, "/api/maps/" + map + "/pins/" + gate, null).andExpect(status().isOk());
        owner.get(guidePath(map, first)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pins.length()").value(1))
                .andExpect(jsonPath("$.data.pins[0].pinId").value(temple.toString()));
        owner.get("/api/maps/" + map + "/pins/" + temple + "/guides").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void separatesReadabilityFromWritePermission() throws Exception {
        Client alice = registered("alice@example.test");
        Client bob = registered("bob@example.test");
        Client admin = administrator();
        UUID publicMap = createMap(alice, "公开散步", "public");
        UUID privateMap = createMap(alice, "南京私藏", "private");
        UUID publicGuide = createGuide(alice, publicMap, "公开攻略", "正文。", "https://example.test/pub");
        UUID privateGuide = createGuide(alice, privateMap, "私有攻略", "正文。", null);

        // 他人私有地图整体不可见：读与写一律 404。
        bob.get(guidePath(privateMap, privateGuide)).andExpect(status().isNotFound());
        bob.send(HttpMethod.PATCH, guidePath(privateMap, privateGuide), guide("越权", "正文。", null))
                .andExpect(status().isNotFound());

        // 他人公开地图可读不可写：写入 403。
        bob.get(guidePath(publicMap, publicGuide)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("公开攻略"));
        bob.get(guidesPath(publicMap)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
        bob.send(HttpMethod.PATCH, guidePath(publicMap, publicGuide), guide("越权改名", "正文。", null))
                .andExpect(status().isForbidden());
        bob.send(HttpMethod.DELETE, guidePath(publicMap, publicGuide), null).andExpect(status().isForbidden());
        bob.send(HttpMethod.PUT, guidePinsPath(publicMap, publicGuide), pinOrder())
                .andExpect(status().isForbidden());

        // 管理员可改他人公开地图的攻略，但不能碰他人私有地图。
        admin.send(HttpMethod.PATCH, guidePath(publicMap, publicGuide), guide("管理员修订", "改写正文。", null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("管理员修订"));
        admin.send(HttpMethod.PATCH, guidePath(privateMap, privateGuide), guide("管理员", "正文。", null))
                .andExpect(status().isNotFound());

        // 游客可以读公开攻略，但写操作需要登录。
        Client guest = new Client();
        guest.get(guidePath(publicMap, publicGuide)).andExpect(status().isOk());
        guest.send(HttpMethod.POST, guidesPath(publicMap), guide("游客攻略", "正文。", null))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsInvalidGuideContentAndForeignLinks() throws Exception {
        Client owner = registered("alice@example.test");
        UUID map = createMap(owner, "南京散步", "private");
        UUID otherMap = createMap(owner, "另一张", "private");
        UUID gate = createPin(owner, map, "中华门", 118.77, 32.01);
        UUID foreign = createPin(owner, otherMap, "外图图钉", 118.7, 32.0);
        UUID guide = createGuide(owner, map, "秦淮半日", "正文。", null);

        owner.send(HttpMethod.POST, guidesPath(map), guide("   ", "正文。", null)).andExpect(status().isBadRequest());
        owner.send(HttpMethod.POST, guidesPath(map), guide("a".repeat(121), "正文。", null))
                .andExpect(status().isBadRequest());
        owner.send(HttpMethod.POST, guidesPath(map), guide("标题", "   ", null)).andExpect(status().isBadRequest());
        owner.send(HttpMethod.POST, guidesPath(map), guide("标题", "正".repeat(5001), null))
                .andExpect(status().isBadRequest());
        owner.send(HttpMethod.POST, guidesPath(map), guide("标题", "正文。", "ftp://example.test/a"))
                .andExpect(status().isBadRequest());
        owner.send(HttpMethod.POST, guidesPath(map), guide("标题", "正文。", "https://"))
                .andExpect(status().isBadRequest());
        owner.send(HttpMethod.POST, guidesPath(map), guide("标题", "正文。", "https://example.test/a b"))
                .andExpect(status().isBadRequest());
        owner.send(HttpMethod.POST, guidesPath(map), guide("标题", "正文。", "https://" + "a".repeat(2048)))
                .andExpect(status().isBadRequest());

        // 关联里出现别的地图的图钉、重复编号或空元素都必须拒绝。
        owner.send(HttpMethod.PUT, guidePinsPath(map, guide), pinOrder(gate, foreign))
                .andExpect(status().isBadRequest());
        owner.send(HttpMethod.PUT, guidePinsPath(map, guide), pinOrder(gate, gate))
                .andExpect(status().isBadRequest());
        owner.send(HttpMethod.PUT, guidePinsPath(map, guide),
                Map.of("pinIds", Arrays.asList(gate.toString(), null))).andExpect(status().isBadRequest());

        // 关联不成立的请求不应留下半成品。
        owner.get(guidePath(map, guide)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pins.length()").value(0));
        owner.send(HttpMethod.PUT, guidePinsPath(map, UUID.randomUUID()), pinOrder(gate))
                .andExpect(status().isNotFound());
        owner.send(HttpMethod.PATCH, "/api/maps/" + map + "/guides/not-a-uuid", guide("标题", "正文。", null))
                .andExpect(status().isBadRequest());
    }

    private Instant mapUpdatedAt(Client owner) throws Exception {
        JsonNode map = body(owner.get("/api/me/maps?cityId=" + CITY).andReturn()).get(0);
        return Instant.parse(map.get("updatedAt").asText());
    }

    private String guidesPath(UUID map) {
        return "/api/maps/" + map + "/guides";
    }

    private String guidePath(UUID map, UUID guide) {
        return guidesPath(map) + "/" + guide;
    }

    private String guidePinsPath(UUID map, UUID guide) {
        return guidePath(map, guide) + "/pins";
    }

    private Map<String, Object> guide(String title, String body, String sourceUrl) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("title", title);
        payload.put("body", body);
        payload.put("sourceUrl", sourceUrl);
        return payload;
    }

    private Map<String, Object> pinOrder(UUID... pinIds) {
        var ordered = new ArrayList<String>();
        for (UUID pin : pinIds) ordered.add(pin.toString());
        return Map.of("pinIds", ordered);
    }

    private Map<String, Object> pin(String name, double longitude, double latitude) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("name", name);
        payload.put("description", null);
        payload.put("longitude", longitude);
        payload.put("latitude", latitude);
        return payload;
    }

    private UUID createPin(Client owner, UUID map, String name, double longitude, double latitude) throws Exception {
        JsonNode created = body(owner.send(HttpMethod.POST, "/api/maps/" + map + "/pins",
                pin(name, longitude, latitude)).andExpect(status().isCreated()).andReturn());
        return UUID.fromString(created.get("id").asText());
    }

    private UUID createGuide(Client owner, UUID map, String title, String body, String sourceUrl) throws Exception {
        JsonNode created = body(owner.send(HttpMethod.POST, guidesPath(map), guide(title, body, sourceUrl))
                .andExpect(status().isCreated()).andReturn());
        assertEquals("manual", created.get("origin").asText());
        return UUID.fromString(created.get("id").asText());
    }

    private Connection ownerConnection() throws SQLException {
        return DriverManager.getConnection(testUrl(), "guidepins_test_owner", "");
    }

    private String testUrl() {
        String url = System.getenv("GUIDEPINS_TEST_DB_URL");
        // 清理操作只允许命名明确的本机隔离测试库，拒绝远程地址和业务库名。
        if (url == null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/guidepins_backend_test")) {
            throw new IllegalStateException("集成测试仅允许本机 guidepins_backend_test 数据库");
        }
        return url;
    }

    private Map<String, String> credentials(String identifier) {
        return Map.of(identifier.contains("@") ? "email" : "phone", identifier, "password", PASSWORD);
    }

    private Client registered(String identifier) throws Exception {
        new Client().send(HttpMethod.POST, "/api/auth/register", credentials(identifier))
                .andExpect(status().isCreated());
        return login(identifier);
    }

    private Client login(String identifier) throws Exception {
        Client client = new Client();
        var result = client.send(HttpMethod.POST, "/api/auth/login", credentials(identifier))
                .andExpect(status().isOk()).andReturn();
        client.userId = UUID.fromString(body(result).get("id").asText());
        return client;
    }

    private Client administrator() throws Exception {
        Client admin = registered("admin@example.test");
        try (Connection connection = ownerConnection()) {
            new JdbcTemplate(new SingleConnectionDataSource(connection, true)).update(
                    "UPDATE public.user_account SET role = 'admin' WHERE id = ?", admin.userId);
        }
        return admin;
    }

    private UUID createMap(Client owner, String name, String visibility) throws Exception {
        JsonNode map = body(owner.send(HttpMethod.POST, "/api/cities/" + CITY + "/maps",
                        Map.of("name", name, "visibility", visibility))
                .andExpect(status().isCreated()).andReturn());
        return UUID.fromString(map.get("id").asText());
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsByteArray()).get("data");
    }

    /** 模拟浏览器保存 Cookie，并使用真实 /csrf 响应，避免测试工具绕过防伪校验。 */
    private class Client {
        final Map<String, String> cookies = new LinkedHashMap<>();
        String csrf;
        UUID userId;
        MvcResult last;

        ResultActions get(String path) throws Exception {
            return send(HttpMethod.GET, path, null);
        }

        void refreshCsrf() throws Exception {
            csrf = body(get("/api/auth/csrf").andExpect(status().isOk()).andReturn()).get("token").asText();
        }

        Cookie[] cookieArray() {
            return cookies.entrySet().stream().map(entry -> new Cookie(entry.getKey(), entry.getValue())).toArray(Cookie[]::new);
        }

        ResultActions send(HttpMethod method, String path, Object payload) throws Exception {
            if (method != HttpMethod.GET && csrf == null) refreshCsrf();
            // HttpMethod 是普通类不是枚举，不能用于 switch；这里按需构造请求。
            MockHttpServletRequestBuilder request;
            if (method == HttpMethod.POST) {
                request = MockMvcRequestBuilders.post(path);
            } else if (method == HttpMethod.PUT) {
                request = MockMvcRequestBuilders.put(path);
            } else if (method == HttpMethod.PATCH) {
                request = MockMvcRequestBuilders.patch(path);
            } else if (method == HttpMethod.DELETE) {
                request = MockMvcRequestBuilders.delete(path);
            } else if (method == HttpMethod.GET) {
                request = MockMvcRequestBuilders.get(path);
            } else {
                throw new IllegalArgumentException(method.name());
            }
            Cookie[] jar = cookieArray();
            if (jar.length > 0) request.cookie(jar);
            if (payload != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(payload));
            if (method != HttpMethod.GET) request.header("X-CSRF-TOKEN", csrf);
            ResultActions result = mvc.perform(request);
            last = result.andReturn();
            // 所有场景同时校验统一外层，包括失败响应，避免局部接口漏包装。
            JsonNode envelope = json.readTree(last.getResponse().getContentAsByteArray());
            assertEquals(3, envelope.size());
            assertEquals(last.getResponse().getStatus(), envelope.get("code").asInt());
            assertTrue(envelope.get("message").isTextual());
            assertFalse(envelope.get("message").asText().isBlank());
            assertTrue(envelope.has("data"));
            if (last.getResponse().getStatus() >= 400) assertTrue(envelope.get("data").isNull());
            for (String header : last.getResponse().getHeaders("Set-Cookie")) {
                String[] pair = header.split(";", 2)[0].split("=", 2);
                if (header.contains("Max-Age=0")) cookies.remove(pair[0]);
                else cookies.put(pair[0], pair[1]);
            }
            if (method == HttpMethod.POST && (path.endsWith("/login") || path.endsWith("/logout"))
                    && last.getResponse().getStatus() < 300) csrf = null;
            return result;
        }
    }
}

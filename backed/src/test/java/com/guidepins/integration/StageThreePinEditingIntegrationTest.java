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
import java.util.LinkedHashMap;
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

/** 独立 PostgreSQL 上验证阶段三图钉编辑：落钉、改位置、改名、删钉与地图更新时间的推进规则。 */
@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.datasource.url=${GUIDEPINS_TEST_DB_URL}",
        "spring.datasource.username=guidepins_app",
        "spring.datasource.password=",
        "guidepins.auth.cookie-secure=true"
})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "GUIDEPINS_TEST_DB_URL", matches = ".+")
class StageThreePinEditingIntegrationTest {
    private static final UUID CITY = UUID.fromString("32010000-0000-4000-8000-000000000001");
    private static final String PASSWORD = "GuidePins-Test-123!";
    private static final Path BASEMAP = Path.of("target", "stage-three-test.pmtiles").toAbsolutePath();

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;

    @DynamicPropertySource
    static void fileProperty(DynamicPropertyRegistry registry) throws Exception {
        Files.write(BASEMAP, new byte[] {1, 2, 3, 4, 5});
        registry.add("guidepins.basemap.file", BASEMAP::toString);
    }

    @AfterAll
    static void removeFile() throws Exception {
        Files.deleteIfExists(BASEMAP);
    }

    @BeforeEach
    void resetIsolatedDatabase() throws Exception {
        try (Connection connection = ownerConnection()) {
            var sql = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            sql.execute("TRUNCATE public.pin_guide, public.guide, public.map_pin, public.travel_map, "
                    + "public.auth_session, public.user_account, public.city");
            sql.update("INSERT INTO public.city (id, code, name, basemap_path, west, south, east, north) "
                    + "VALUES (?, '320100', '南京', '/maps/nanjing/v1/nanjing-z16.pmtiles', 118.3, 31.2, 119.2, 32.6)", CITY);
        }
    }

    @Test
    void createsEditsAndDeletesPinsOnOwnedMap() throws Exception {
        Client owner = registered("alice@example.test");
        UUID map = createMap(owner, "南京散步", "private");

        JsonNode created = body(owner.send(HttpMethod.POST, pinsPath(map),
                pin("中华门", "城门说明", 118.77, 32.01)).andExpect(status().isCreated()).andReturn());
        assertTrue(created.has("id"));
        assertEquals("中华门", created.get("name").asText());
        assertEquals("城门说明", created.get("description").asText());
        assertEquals(118.77, created.get("longitude").asDouble(), 1e-9);
        owner.get("/api/maps/" + map).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pins.length()").value(1))
                .andExpect(jsonPath("$.data.pins[0].name").value("中华门"));

        UUID pin = UUID.fromString(created.get("id").asText());
        JsonNode moved = body(owner.send(HttpMethod.PATCH, pinPath(map, pin),
                pin("夫子庙", "改了说明", 118.79, 32.02)).andExpect(status().isOk()).andReturn());
        assertEquals("夫子庙", moved.get("name").asText());
        assertEquals(118.79, moved.get("longitude").asDouble(), 1e-9);
        owner.get("/api/maps/" + map).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pins[0].description").value("改了说明"));

        owner.send(HttpMethod.DELETE, pinPath(map, pin), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("图钉已删除"));
        owner.get("/api/maps/" + map).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pins.length()").value(0));

        // 说明为 null 表示清空说明，落钉后返回 null。
        JsonNode anonymous = body(owner.send(HttpMethod.POST, pinsPath(map), pin("无说明", null, 118.8, 32.0))
                .andExpect(status().isCreated()).andReturn());
        assertTrue(anonymous.get("description").isNull());
    }

    @Test
    void separatesReadabilityFromWritePermission() throws Exception {
        Client alice = registered("alice@example.test");
        Client bob = registered("bob@example.test");
        Client admin = administrator();
        UUID publicMap = createMap(alice, "公开散步", "public");
        UUID privateMap = createMap(alice, "南京私藏", "private");
        UUID pin = UUID.fromString(body(alice.send(HttpMethod.POST, pinsPath(publicMap),
                pin("中华门", null, 118.77, 32.01)).andExpect(status().isCreated()).andReturn())
                .get("id").asText());

        // 同一账户在私有地图上完全不可见，越权写入返回 404；公开地图可见但只有管理员可写，返回 403。
        bob.send(HttpMethod.POST, pinsPath(privateMap), pin("越权落钉", null, 118.7, 32.0))
                .andExpect(status().isNotFound());
        bob.send(HttpMethod.PATCH, pinPath(publicMap, pin), pin("越权改名", null, 118.7, 32.0))
                .andExpect(status().isForbidden());
        bob.send(HttpMethod.DELETE, pinPath(publicMap, pin), null).andExpect(status().isForbidden());
        admin.send(HttpMethod.PATCH, pinPath(privateMap, pin), pin("管理员", null, 118.7, 32.0))
                .andExpect(status().isNotFound());

        admin.send(HttpMethod.PATCH, pinPath(publicMap, pin), pin("管理员修订", "补充", 118.78, 32.02))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("管理员修订"));
        JsonNode added = body(admin.send(HttpMethod.POST, pinsPath(publicMap), pin("管理员落钉", null, 118.9, 32.1))
                .andExpect(status().isCreated()).andReturn());
        assertEquals("管理员落钉", added.get("name").asText());

        // 图钉必须属于路径中的地图，跨地图编号一律按不存在处理。
        UUID otherMap = createMap(alice, "另一张", "public");
        alice.send(HttpMethod.DELETE, pinPath(otherMap, pin), null).andExpect(status().isNotFound());

        Client guest = new Client();
        guest.send(HttpMethod.POST, pinsPath(publicMap), pin("游客落钉", null, 118.7, 32.0))
                .andExpect(status().isUnauthorized());
        guest.get("/api/maps/" + publicMap).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pins.length()").value(2));
    }

    @Test
    void rejectsInvalidPinContent() throws Exception {
        Client owner = registered("alice@example.test");
        UUID map = createMap(owner, "南京散步", "private");
        UUID pin = UUID.fromString(body(owner.send(HttpMethod.POST, pinsPath(map),
                pin("中华门", null, 118.77, 32.01)).andExpect(status().isCreated()).andReturn())
                .get("id").asText());

        owner.send(HttpMethod.POST, pinsPath(map), pin("   ", null, 118.7, 32.0)).andExpect(status().isBadRequest());
        owner.send(HttpMethod.POST, pinsPath(map), pin("a".repeat(121), null, 118.7, 32.0)).andExpect(status().isBadRequest());
        owner.send(HttpMethod.POST, pinsPath(map), pin("长说明", "说".repeat(2001), 118.7, 32.0)).andExpect(status().isBadRequest());
        owner.send(HttpMethod.POST, pinsPath(map), pin("越界经度", null, 180.5, 32.0)).andExpect(status().isBadRequest());
        owner.send(HttpMethod.POST, pinsPath(map), pin("越界纬度", null, 118.7, -90.5)).andExpect(status().isBadRequest());
        owner.send(HttpMethod.POST, pinsPath(map), pin("缺坐标", null, 118.7, 32.0).entrySet().stream()
                .filter(entry -> !entry.getKey().equals("latitude"))
                .collect(LinkedHashMap::new, (target, entry) -> target.put(entry.getKey(), entry.getValue()),
                        LinkedHashMap::putAll))
                .andExpect(status().isBadRequest());
        owner.send(HttpMethod.POST, pinsPath(map), Map.of("name", "坏坐标", "longitude", "abc",
                "latitude", 32.0)).andExpect(status().isBadRequest());
        owner.send(HttpMethod.PATCH, pinPath(map, pin), pin("", null, 118.7, 32.0)).andExpect(status().isBadRequest());
        owner.send(HttpMethod.PATCH, pinPath(map, UUID.randomUUID()), pin("不存在", null, 118.7, 32.0))
                .andExpect(status().isNotFound());
        owner.send(HttpMethod.POST, "/api/maps/not-a-uuid/pins", pin("坏编号", null, 118.7, 32.0))
                .andExpect(status().isBadRequest());

        // 校验失败不应留下半成品：地图上仍然只有最初那枚图钉。
        owner.get("/api/maps/" + map).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pins.length()").value(1))
                .andExpect(jsonPath("$.data.pins[0].name").value("中华门"));
    }

    @Test
    void advancesMapTimeOnlyWhenPinContentActuallyChanges() throws Exception {
        Client owner = registered("alice@example.test");
        UUID map = createMap(owner, "南京散步", "private");
        JsonNode initial = body(owner.get("/api/me/maps?cityId=" + CITY).andReturn()).get(0);
        Instant createdAt = Instant.parse(initial.get("createdAt").asText());
        Instant afterMap = Instant.parse(initial.get("updatedAt").asText());

        UUID pin = UUID.fromString(body(owner.send(HttpMethod.POST, pinsPath(map),
                pin("中华门", "城门说明", 118.77, 32.01)).andExpect(status().isCreated()).andReturn())
                .get("id").asText());
        Instant afterCreate = mapUpdatedAt(owner);

        JsonNode unchanged = body(owner.send(HttpMethod.PATCH, pinPath(map, pin),
                pin("  中华门  ", "  城门说明  ", 118.77, 32.01)).andExpect(status().isOk()).andReturn());
        assertEquals("中华门", unchanged.get("name").asText());
        assertEquals(afterCreate, mapUpdatedAt(owner));

        owner.send(HttpMethod.PATCH, pinPath(map, pin), pin("中华门", "新的说明", 118.77, 32.01))
                .andExpect(status().isOk());
        Instant afterEdit = mapUpdatedAt(owner);

        owner.send(HttpMethod.PATCH, pinPath(map, pin), pin("中华门", "新的说明", 118.8, 32.05))
                .andExpect(status().isOk());
        Instant afterMove = mapUpdatedAt(owner);

        owner.send(HttpMethod.DELETE, pinPath(map, pin), null).andExpect(status().isOk());
        Instant afterDelete = mapUpdatedAt(owner);

        assertTrue(afterCreate.isAfter(afterMap));
        assertTrue(afterEdit.isAfter(afterCreate));
        assertTrue(afterMove.isAfter(afterEdit));
        assertTrue(afterDelete.isAfter(afterMove));
        assertEquals(createdAt, Instant.parse(body(owner.get("/api/me/maps?cityId=" + CITY).andReturn())
                .get(0).get("createdAt").asText()));
    }

    private Instant mapUpdatedAt(Client owner) throws Exception {
        JsonNode map = body(owner.get("/api/me/maps?cityId=" + CITY).andReturn()).get(0);
        return Instant.parse(map.get("updatedAt").asText());
    }

    private String pinsPath(UUID map) {
        return "/api/maps/" + map + "/pins";
    }

    private String pinPath(UUID map, UUID pin) {
        return pinsPath(map) + "/" + pin;
    }

    private Map<String, Object> pin(String name, String description, double longitude, double latitude) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("name", name);
        payload.put("description", description);
        payload.put("longitude", longitude);
        payload.put("latitude", latitude);
        return payload;
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
        new Client().send(HttpMethod.POST, "/api/auth/register", credentials(identifier)).andExpect(status().isCreated());
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

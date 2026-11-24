package com.guidepins.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidepins.auth.service.AuthService;
import com.guidepins.publicmap.mapper.PublicMapDataMapper;
import com.guidepins.publicmap.mapper.mybatis.TravelMapSqlMapper;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HexFormat;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** 独立 PostgreSQL 上验证安全链、事务、XML 和实际最小授权，不接触远程业务库。 */
@SpringBootTest(properties = {
        "spring.config.import=",
        "spring.datasource.url=${GUIDEPINS_TEST_DB_URL}",
        "spring.datasource.username=guidepins_app",
        "spring.datasource.password=",
        "guidepins.auth.cookie-secure=true"
})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "GUIDEPINS_TEST_DB_URL", matches = ".+")
class StageTwoBackendIntegrationTest {
    private static final UUID CITY = UUID.fromString("32010000-0000-4000-8000-000000000001");
    private static final String PASSWORD = "GuidePins-Test-123!";
    private static final Path BASEMAP = Path.of("target", "stage-two-test.pmtiles").toAbsolutePath();

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private PasswordEncoder passwords;
    @Autowired private AuthService auth;
    @Autowired private PublicMapDataMapper maps;
    @Autowired private TravelMapSqlMapper mapSql;

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
            sql.execute("TRUNCATE public.map_pin, public.travel_map, public.auth_session, public.user_account, public.city");
            sql.update("INSERT INTO public.city (id, code, name, basemap_path, west, south, east, north) "
                    + "VALUES (?, '320100', '南京', '/maps/nanjing/v1/nanjing-z16.pmtiles', 118.3, 31.2, 119.2, 32.6)", CITY);
        }
    }

    @Test
    void registersAndLogsInWithBothNormalizedIdentifiers() throws Exception {
        Client email = registered("  ALICE@EXAMPLE.TEST  ");
        email.get("/api/auth/me").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("alice@example.test"))
                .andExpect(jsonPath("$.data.role").value("user"))
                .andExpect(jsonPath("$.data.avatarPath").isEmpty())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.token").doesNotExist());
        Client phone = registered("+8613800000000");
        phone.get("/api/auth/me").andExpect(status().isOk()).andExpect(jsonPath("$.data.phone").value("+8613800000000"));
        assertNotEquals(email.userId, phone.userId);
        assertNull(email.last.getRequest().getSession(false));
        assertFalse(email.cookies.containsKey("JSESSIONID"));

        String raw = email.cookies.get("GUIDEPINS_SESSION");
        assertTrue(raw.matches("[A-Za-z0-9_-]{43}"));
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(raw.getBytes(StandardCharsets.US_ASCII)));
        try (Connection connection = ownerConnection()) {
            var sql = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            String hash = sql.queryForObject("SELECT password_hash FROM public.user_account WHERE id = ?", String.class, email.userId);
            assertNotNull(hash);
            assertTrue(hash.startsWith("$argon2id$"));
            assertTrue(passwords.matches(PASSWORD, hash));
            assertEquals(604800L, sql.queryForObject("SELECT extract(epoch FROM expires_at - created_at)::bigint "
                    + "FROM public.auth_session WHERE token_hash = ?", Long.class, digest));
            assertEquals(0, sql.queryForObject("SELECT count(*) FROM public.auth_session WHERE token_hash = ?", Integer.class, raw));
        }
        Client reopened = new Client();
        reopened.cookies.putAll(email.cookies);
        reopened.get("/api/auth/me").andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(email.userId.toString()));
    }

    @Test
    void validatesCredentialsAndNeverAcceptsClientRoleOrOwner() throws Exception {
        registered("alice@example.test");
        Client guest = new Client();
        guest.send(HttpMethod.POST, "/api/auth/register", credentials("alice@example.test")).andExpect(status().isConflict());
        guest.send(HttpMethod.POST, "/api/auth/register", Map.of("email", "broken", "password", PASSWORD)).andExpect(status().isBadRequest());
        guest.send(HttpMethod.POST, "/api/auth/register", Map.of("phone", "13800000000", "password", PASSWORD)).andExpect(status().isBadRequest());
        guest.send(HttpMethod.POST, "/api/auth/register", Map.of("email", "bob@example.test", "password", "short")).andExpect(status().isBadRequest());
        guest.send(HttpMethod.POST, "/api/auth/register", Map.of("email", "bob@example.test", "phone", "+8613900000000", "password", PASSWORD)).andExpect(status().isBadRequest());
        guest.send(HttpMethod.POST, "/api/auth/register", Map.of("password", PASSWORD)).andExpect(status().isBadRequest());
        guest.send(HttpMethod.POST, "/api/auth/register", Map.of("email", "bob@example.test", "password", PASSWORD, "role", "admin")).andExpect(status().isBadRequest());
        guest.send(HttpMethod.POST, "/api/auth/login", Map.of("email", "alice@example.test", "password", "incorrect")).andExpect(status().isUnauthorized());
        guest.send(HttpMethod.POST, "/api/auth/login", credentials("missing@example.test")).andExpect(status().isUnauthorized());
        Client owner = login("alice@example.test");
        owner.send(HttpMethod.POST, "/api/cities/" + CITY + "/maps", Map.of("name", "伪造归属", "ownerId", UUID.randomUUID())).andExpect(status().isBadRequest());
        owner.send(HttpMethod.POST, "/api/cities/" + CITY + "/maps", Map.of("name", "   ")).andExpect(status().isBadRequest());
        owner.send(HttpMethod.POST, "/api/cities/" + CITY + "/maps", Map.of("name", "a".repeat(121))).andExpect(status().isBadRequest());
        owner.send(HttpMethod.POST, "/api/cities/" + CITY + "/maps", Map.of("name", "错误状态", "visibility", "friends")).andExpect(status().isBadRequest());
        owner.send(HttpMethod.POST, "/api/cities/" + UUID.randomUUID() + "/maps", Map.of("name", "不存在的地市")).andExpect(status().isNotFound());
        owner.get("/api/me/maps").andExpect(status().isBadRequest());
        guest.get("/api/maps/invalid-uuid").andExpect(status().isBadRequest());
    }

    @Test
    void enforcesRealCsrfTokensAndCookieAttributes() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(credentials("alice@example.test")))).andExpect(status().isForbidden());
        Client owner = new Client();
        owner.send(HttpMethod.POST, "/api/auth/register", credentials("alice@example.test")).andExpect(status().isCreated());
        String oldCsrf = owner.csrf;
        MvcResult login = owner.send(HttpMethod.POST, "/api/auth/login", credentials("alice@example.test")).andExpect(status().isOk()).andReturn();
        owner.userId = UUID.fromString(body(login).get("id").asText());
        String setCookie = login.getResponse().getHeaders("Set-Cookie").stream()
                .filter(value -> value.startsWith("GUIDEPINS_SESSION=")).findFirst().orElseThrow();
        assertTrue(setCookie.contains("HttpOnly"));
        assertTrue(setCookie.contains("Secure"));
        assertTrue(setCookie.contains("SameSite=Lax"));
        assertTrue(setCookie.contains("Max-Age=604800"));
        assertTrue(setCookie.contains("Path=/"));
        assertFalse(owner.cookies.containsKey("GUIDEPINS_CSRF"));
        owner.refreshCsrf();
        mvc.perform(post("/api/cities/" + CITY + "/maps").cookie(owner.cookieArray())
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"非法请求\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/cities/" + CITY + "/maps").cookie(owner.cookieArray())
                .header("X-CSRF-TOKEN", oldCsrf).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"旧令牌\"}")).andExpect(status().isForbidden());
        createMap(owner, "合法请求", "private");
        Client guest = new Client();
        guest.send(HttpMethod.POST, "/api/cities/" + CITY + "/maps", Map.of("name", "未登录"))
                .andExpect(status().isUnauthorized());
        guest.get("/api/auth/me").andExpect(status().isUnauthorized());
    }

    @Test
    void expiresAndDisablesSessionsAndLogsOutOnlyCurrentDevice() throws Exception {
        Client first = registered("alice@example.test");
        Client second = login("alice@example.test");
        assertNotEquals(first.cookies.get("GUIDEPINS_SESSION"), second.cookies.get("GUIDEPINS_SESSION"));
        first.send(HttpMethod.POST, "/api/auth/logout", null).andExpect(status().isOk()).andExpect(jsonPath("$.message").value("退出成功"));
        first.get("/api/auth/me").andExpect(status().isUnauthorized());
        second.get("/api/auth/me").andExpect(status().isOk());
        try (Connection connection = ownerConnection()) {
            var sql = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            sql.update("UPDATE public.user_account SET status = 0 WHERE id = ?", second.userId);
            second.get("/api/auth/me").andExpect(status().isUnauthorized());
            new Client().send(HttpMethod.POST, "/api/auth/login", credentials("alice@example.test")).andExpect(status().isUnauthorized());
            sql.update("UPDATE public.user_account SET status = 1, role = 'admin' WHERE id = ?", second.userId);
            second.get("/api/auth/me").andExpect(status().isOk()).andExpect(jsonPath("$.data.role").value("admin"));
            sql.update("UPDATE public.auth_session SET created_at = CURRENT_TIMESTAMP - interval '8 days', "
                    + "expires_at = CURRENT_TIMESTAMP - interval '1 day' WHERE user_id = ?", second.userId);
            second.get("/api/auth/me").andExpect(status().isUnauthorized());
            Client active = registered("bob@example.test");
            auth.cleanExpiredSessions();
            assertEquals(0, sql.queryForObject("SELECT count(*) FROM public.auth_session WHERE user_id = ?", Integer.class, second.userId));
            active.get("/api/auth/me").andExpect(status().isOk());
        }
    }

    @Test
    void isolatesPrivateMapsBetweenOwnersGuestAndAdministrator() throws Exception {
        Client alice = registered("alice@example.test");
        Client bob = registered("bob@example.test");
        Client admin = administrator();
        UUID privateMap = createMap(alice, "南京私藏", null);
        createMap(bob, "另一份私藏", "private");
        Client guest = new Client();
        guest.get("/api/cities/" + CITY + "/maps").andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        alice.get("/api/maps/" + privateMap).andExpect(status().isOk());
        for (Client other : new Client[] {guest, bob, admin}) {
            other.get("/api/maps/" + privateMap).andExpect(status().isNotFound());
        }
        bob.send(HttpMethod.PATCH, "/api/maps/" + privateMap + "/name", Map.of("name", "越权改名")).andExpect(status().isNotFound());
        admin.send(HttpMethod.PATCH, "/api/maps/" + privateMap + "/visibility", Map.of("visibility", "public")).andExpect(status().isNotFound());
        alice.get("/api/me/maps?cityId=" + CITY).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1)).andExpect(jsonPath("$.data[0].ownerId").value(alice.userId.toString()));
        bob.get("/api/me/maps?cityId=" + CITY).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1)).andExpect(jsonPath("$.data[0].ownerId").value(bob.userId.toString()));
        admin.get("/api/me/maps?cityId=" + CITY).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void protectsPinsAndRevokesAccessWhenOwnerMakesMapPrivate() throws Exception {
        Client alice = registered("alice@example.test");
        Client bob = registered("bob@example.test");
        Client admin = administrator();
        UUID map = createMap(alice, "公开散步", "public");
        try (Connection connection = ownerConnection()) {
            new JdbcTemplate(new SingleConnectionDataSource(connection, true)).update(
                    "INSERT INTO public.map_pin (map_id, name, description, longitude, latitude) VALUES (?, '中华门', '城门说明', 118.77, 32.01)", map);
        }
        Client guest = new Client();
        for (Client viewer : new Client[] {guest, bob, admin}) {
            viewer.get("/api/maps/" + map).andExpect(status().isOk()).andExpect(jsonPath("$.data.pins[0].name").value("中华门"));
        }
        bob.send(HttpMethod.PATCH, "/api/maps/" + map + "/name", Map.of("name", "越权名称")).andExpect(status().isForbidden());
        admin.send(HttpMethod.PATCH, "/api/maps/" + map + "/name", Map.of("name", "管理员修订"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.ownerId").value(alice.userId.toString()))
                .andExpect(jsonPath("$.data.visibility").value("public"));
        admin.send(HttpMethod.PATCH, "/api/maps/" + map + "/visibility", Map.of("visibility", "private")).andExpect(status().isForbidden());
        alice.send(HttpMethod.PATCH, "/api/maps/" + map + "/visibility", Map.of("visibility", "private")).andExpect(status().isOk());
        for (Client viewer : new Client[] {guest, bob, admin}) {
            viewer.get("/api/maps/" + map).andExpect(status().isNotFound());
        }
        guest.get("/api/cities/" + CITY + "/maps").andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(0));
        assertTrue(maps.findPinsByMapId(map, null).isEmpty());
        assertTrue(maps.findPinsByMapId(map, admin.userId).isEmpty());
        assertEquals(1, maps.findPinsByMapId(map, alice.userId).size());
        alice.get("/api/maps/" + map).andExpect(status().isOk()).andExpect(jsonPath("$.data.pins[0].name").value("中华门"));
    }

    @Test
    void updatesTimeOnlyForActualSuccessfulChanges() throws Exception {
        Client owner = registered("alice@example.test");
        UUID map = createMap(owner, "南京散步", "private");
        JsonNode original = body(owner.get("/api/me/maps?cityId=" + CITY).andReturn()).get(0);
        JsonNode noChange = body(owner.send(HttpMethod.PATCH, "/api/maps/" + map + "/name", Map.of("name", "  南京散步  "))
                .andExpect(status().isOk()).andReturn());
        assertEquals(original.get("updatedAt"), noChange.get("updatedAt"));
        JsonNode renamed = body(owner.send(HttpMethod.PATCH, "/api/maps/" + map + "/name", Map.of("name", "秦淮散步"))
                .andExpect(status().isOk()).andReturn());
        assertTrue(Instant.parse(renamed.get("updatedAt").asText()).isAfter(Instant.parse(original.get("updatedAt").asText())));
        assertEquals(original.get("createdAt"), renamed.get("createdAt"));
        JsonNode sameVisibility = body(owner.send(HttpMethod.PATCH, "/api/maps/" + map + "/visibility", Map.of("visibility", "private"))
                .andExpect(status().isOk()).andReturn());
        assertEquals(renamed.get("updatedAt"), sameVisibility.get("updatedAt"));
        owner.get("/api/maps/" + map).andExpect(status().isOk());
        owner.send(HttpMethod.PATCH, "/api/maps/" + map + "/name", Map.of("name", "   ")).andExpect(status().isBadRequest());
        JsonNode afterRead = body(owner.get("/api/me/maps?cityId=" + CITY).andReturn()).get(0);
        assertEquals(renamed.get("updatedAt"), afterRead.get("updatedAt"));
        JsonNode madePublic = body(owner.send(HttpMethod.PATCH, "/api/maps/" + map + "/visibility", Map.of("visibility", "public"))
                .andExpect(status().isOk()).andReturn());
        assertTrue(Instant.parse(madePublic.get("updatedAt").asText()).isAfter(Instant.parse(renamed.get("updatedAt").asText())));
        assertEquals(original.get("createdAt"), madePublic.get("createdAt"));
    }

    @Test
    void rechecksDatabaseRoleAndVisibilityInsideWriteSqlAndLimitsDatabasePrivileges() throws Exception {
        Client owner = registered("alice@example.test");
        Client admin = administrator();
        UUID map = createMap(owner, "公开散步", "public");
        try (Connection connection = ownerConnection()) {
            var sql = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            sql.update("UPDATE public.user_account SET role = 'user' WHERE id = ?", admin.userId);
            assertNull(mapSql.updateName(map, admin.userId, "旧管理员身份"));
            sql.update("UPDATE public.user_account SET role = 'admin' WHERE id = ?", admin.userId);
            sql.update("UPDATE public.travel_map SET visibility = 'private' WHERE id = ?", map);
            assertNull(mapSql.updateName(map, admin.userId, "旧公开状态"));
            assertNull(mapSql.updateVisibility(map, admin.userId, "public"));
            sql.update("UPDATE public.user_account SET status = 0 WHERE id = ?", owner.userId);
            assertNull(mapSql.updateName(map, owner.userId, "禁用账号"));
            assertNull(mapSql.insertMap(CITY, owner.userId, "禁用新建", "public"));
        }
        try (Connection connection = DriverManager.getConnection(testUrl(), "guidepins_app", "")) {
            for (String forbidden : new String[] {
                    "UPDATE public.user_account SET role = 'admin'",
                    "UPDATE public.user_account SET status = 1",
                    "INSERT INTO public.user_account (email,password_hash,role) VALUES ('bad@example.test','invalid','admin')",
                    "DELETE FROM public.travel_map",
                    "UPDATE public.travel_map SET owner_id = gen_random_uuid()",
                    "UPDATE public.city SET name = '修改地市'",
                    "INSERT INTO public.map_pin (map_id,name,longitude,latitude) VALUES (gen_random_uuid(),'越权',0,0)"
            }) {
                SQLException error = assertThrows(SQLException.class, () -> connection.createStatement().execute(forbidden));
                assertEquals("42501", error.getSQLState());
            }
        }
    }

    @Test
    void preservesGuestCityAndBasemapRangeEndpoints() throws Exception {
        new Client().get("/api/cities").andExpect(status().isOk()).andExpect(jsonPath("$.data[0].code").value("320100"));
        mvc.perform(get("/maps/nanjing/v1/nanjing-z16.pmtiles").header("Range", "bytes=1-2"))
                .andExpect(status().isPartialContent()).andExpect(header().string("Content-Range", "bytes 1-2/5"))
                .andExpect(content().bytes(new byte[] {2, 3}));
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
        var result = client.send(HttpMethod.POST, "/api/auth/login", credentials(identifier)).andExpect(status().isOk()).andReturn();
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
        var request = new LinkedHashMap<String, Object>();
        request.put("name", name);
        if (visibility != null) request.put("visibility", visibility);
        JsonNode map = body(owner.send(HttpMethod.POST, "/api/cities/" + CITY + "/maps", request)
                .andExpect(status().isCreated()).andReturn());
        assertEquals(owner.userId.toString(), map.get("ownerId").asText());
        assertEquals(visibility == null ? "private" : visibility, map.get("visibility").asText());
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
            var request = request(method, path);
            Cookie[] jar = cookieArray();
            if (jar.length > 0) request.cookie(jar);
            if (payload != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(payload));
            if (method != HttpMethod.GET) request.header("X-CSRF-TOKEN", csrf);
            ResultActions result = mvc.perform(request);
            last = result.andReturn();
            // 所有认证及地图场景同时校验统一外层，包括失败响应，避免局部接口漏包装。
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

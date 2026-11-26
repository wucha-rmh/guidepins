package com.guidepins.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidepins.auth.service.AuthService;
import com.guidepins.offline.mapper.PmtilesExtractorTest;
import jakarta.servlet.http.Cookie;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** 只连接本机隔离库；真实安全链校验下载权限，生成文件再解包核对内容与站序。 */
@SpringBootTest(properties = {"spring.config.import=", "spring.datasource.url=${GUIDEPINS_TEST_DB_URL}",
        "spring.datasource.username=guidepins_app", "spring.datasource.password="})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "GUIDEPINS_TEST_DB_URL", matches = ".+")
class StageFourOfflineIntegrationTest {
    private static final UUID CITY = UUID.fromString("32010000-0000-4000-8000-000000000001");
    private static final String PASSWORD = "GuidePins-Test-123!";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AuthService auth;
    UUID owner, other, admin, publicMap, privateMap, firstPin, secondPin, guide;

    @DynamicPropertySource static void basemap(DynamicPropertyRegistry registry) throws Exception {
        String realRoot = System.getenv("GUIDEPINS_OFFLINE_REAL_BASEMAP_ROOT");
        if (realRoot != null && !realRoot.isBlank()) { registry.add("guidepins.basemap.root", () -> realRoot); return; }
        Path root = Path.of("target", "stage-four-basemap").toAbsolutePath();
        PmtilesExtractorTest.fixture(root.resolve("320100-南京市/test.pmtiles"));
        registry.add("guidepins.basemap.root", root::toString);
    }

    private Connection ownerConnection() throws Exception {
        String url = System.getenv("GUIDEPINS_TEST_DB_URL");
        if (url == null || !url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/guidepins_backend_test"))
            throw new IllegalStateException("仅允许本机 guidepins_backend_test 数据库");
        return DriverManager.getConnection(url, "guidepins_test_owner", "");
    }

    @BeforeEach void prepare() throws Exception {
        try (var connection = ownerConnection()) {
            var sql = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            sql.execute("TRUNCATE public.pin_guide, public.guide, public.map_pin, public.travel_map, public.auth_session, public.user_account, public.city");
            sql.update("INSERT INTO city(id,code,name,basemap_path,west,south,east,north) VALUES (?, '320100','南京','/maps/320100.pmtiles',118.3,31.2,119.2,32.6)", CITY);
        }
        owner = auth.register("owner@example.test", null, PASSWORD).id();
        other = auth.register("other@example.test", null, PASSWORD).id();
        admin = auth.register("admin@example.test", null, PASSWORD).id();
        publicMap = UUID.randomUUID(); privateMap = UUID.randomUUID();
        firstPin = UUID.randomUUID(); secondPin = UUID.randomUUID(); guide = UUID.randomUUID();
        try (var connection = ownerConnection()) {
            var sql = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            sql.update("UPDATE user_account SET role='admin' WHERE id=?", admin);
            sql.update("INSERT INTO travel_map(id,city_id,owner_id,name,visibility) VALUES (?,?,?,'南京散步','public'),(?,?,?,'私人手记','private')",
                    publicMap,CITY,owner,privateMap,CITY,owner);
            sql.update("INSERT INTO map_pin(id,map_id,name,description,longitude,latitude) VALUES (?,?,'夫子庙','沿河慢走',118.79,32.02),(?,?,'新街口','<script>window.injected=true</script>',118.79,32.045)",
                    firstPin,publicMap,secondPin,publicMap);
            sql.update("INSERT INTO guide(id,map_id,title,body,source_url) VALUES (?,?,'秦淮半日','正文 </script><script>window.injected=true</script>','https://example.test/source')",guide,publicMap);
            sql.update("INSERT INTO pin_guide(guide_id,pin_id,position) VALUES (?,?,0),(?,?,1)",guide,secondPin,guide,firstPin);
        }
    }

    private Cookie cookie(String email) { return new Cookie("GUIDEPINS_SESSION", auth.login(email,null,PASSWORD).token()); }
    private String path(UUID map) { return "/api/maps/" + map + "/offline"; }
    private JsonNode unpack(byte[] bytes) throws Exception {
        String html = new String(bytes, StandardCharsets.UTF_8);
        var match = Pattern.compile("<script id=\"payload\" type=\"application/octet-stream\">([^<]+)</script>").matcher(html);
        assertTrue(match.find());
        try (var input = new GZIPInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(match.group(1))))) {
            return json.readTree(input);
        }
    }

    @Test void guestDownloadsPublicMapWithCompleteContentAndNoAccountData() throws Exception {
        var response = mvc.perform(get(path(publicMap))).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control","no-store"))
                .andExpect(header().exists("Content-Disposition"))
                .andExpect(content().contentTypeCompatibleWith("text/html")).andReturn().getResponse();
        byte[] bytes = response.getContentAsByteArray(); JsonNode payload = unpack(bytes);
        assertEquals(1, payload.at("/snapshot/version").asInt());
        assertEquals(2, payload.at("/snapshot/pins").size());
        assertEquals(1, payload.at("/snapshot/guides").size());
        assertEquals(secondPin.toString(),payload.at("/snapshot/guides/0/pins/0/pinId").asText());
        assertEquals(firstPin.toString(),payload.at("/snapshot/guides/0/pins/1/pinId").asText());
        assertTrue(payload.at("/basemap/tiles").size() >= 2);
        assertFalse(payload.toString().contains("owner@example.test"));
        assertFalse(payload.at("/snapshot").has("ownerId"));
        assertFalse(new String(bytes,StandardCharsets.UTF_8).contains("<script>window.injected=true</script>"));
        Path result = Path.of("target","offline-verification"); Files.createDirectories(result);
        Files.write(result.resolve("南京散步.html"),bytes);
        Files.writeString(result.resolve("payload-summary.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(
                java.util.Map.of("fileBytes",bytes.length,"tiles",payload.at("/basemap/tiles").size(),"contents",payload.at("/basemap/blobs").size())));
    }

    @Test void enforcesPrivateMapIsolationAndHandlesEmptyMap() throws Exception {
        mvc.perform(get(path(privateMap))).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(404));
        mvc.perform(get(path(privateMap)).cookie(cookie("other@example.test"))).andExpect(status().isNotFound());
        mvc.perform(get(path(privateMap)).cookie(cookie("admin@example.test"))).andExpect(status().isNotFound());
        var result = mvc.perform(get(path(privateMap)).cookie(cookie("owner@example.test"))).andExpect(status().isOk()).andReturn();
        var payload = unpack(result.getResponse().getContentAsByteArray());
        assertEquals(0,payload.at("/snapshot/pins").size()); assertEquals(0,payload.at("/snapshot/guides").size());
        Files.createDirectories(Path.of("target/offline-verification"));
        Files.write(Path.of("target/offline-verification/南京空地图.html"),result.getResponse().getContentAsByteArray());
        // 公共地图转私人后，新下载立即遵循新的可见性；已有文件仍是用户保存的快照。
        try (var connection = ownerConnection()) {
            new JdbcTemplate(new SingleConnectionDataSource(connection,true)).update("UPDATE travel_map SET visibility='private' WHERE id=?",publicMap);
        }
        mvc.perform(get(path(publicMap))).andExpect(status().isNotFound());
    }

    @Test void rejectsOtherCitiesAndPreservesMapTimestamp() throws Exception {
        var cookie = cookie("owner@example.test");
        JsonNode before = json.readTree(mvc.perform(get("/api/me/maps?cityId="+CITY).cookie(cookie)).andReturn().getResponse().getContentAsByteArray());
        mvc.perform(get(path(publicMap))).andExpect(status().isOk());
        JsonNode after = json.readTree(mvc.perform(get("/api/me/maps?cityId="+CITY).cookie(cookie)).andReturn().getResponse().getContentAsByteArray());
        assertEquals(before,after);
        try (var connection = ownerConnection()) {
            new JdbcTemplate(new SingleConnectionDataSource(connection,true)).update("UPDATE city SET code='320200',name='无锡' WHERE id=?",CITY);
        }
        mvc.perform(get(path(publicMap))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
        mvc.perform(get("/api/maps/not-a-uuid/offline")).andExpect(status().isBadRequest());
    }
}

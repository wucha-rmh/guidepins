package com.guidepins.basemap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guidepins.basemap.controller.BasemapController;
import com.guidepins.basemap.controller.CityBoundaryController;
import com.guidepins.basemap.mapper.impl.FileBasemapMapper;
import com.guidepins.basemap.service.BasemapService;
import com.guidepins.exception.ResourceNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 底图文件按行政区代码寻址：市目录约定、字节范围读取与 404 行为。 */
class BasemapModuleTest {
    @TempDir Path directory;

    @Test
    void readsCityPmtilesAndBoundaryByCode() throws Exception {
        Path cityDirectory = prepareCity("320100", "南京", new byte[] {1, 2, 3, 4, 5});
        try (var context = basemapContext();
                var input = context.getBean(BasemapService.class).pmtiles("320100").getInputStream()) {
            assertArrayEquals(new byte[] {1, 2, 3, 4, 5}, input.readAllBytes());
        }
        try (var context = basemapContext();
                var input = context.getBean(BasemapService.class).boundary("320100").getInputStream()) {
            assertArrayEquals(Files.readAllBytes(cityDirectory.resolve("boundary.geojson")), input.readAllBytes());
        }
    }

    @Test
    void returns404ForUnknownOrMalformedCodes() {
        prepareCity("320100", "南京", new byte[] {1});
        try (var context = basemapContext()) {
            BasemapService basemaps = context.getBean(BasemapService.class);
            // 未制包的代码、路径穿越写法都应被挡在文件访问之前。
            assertThrows(ResourceNotFoundException.class, () -> basemaps.pmtiles("320200"));
            assertThrows(ResourceNotFoundException.class, () -> basemaps.pmtiles("../320100"));
            assertThrows(ResourceNotFoundException.class, () -> basemaps.boundary("3202"));
        }
    }

    @Test
    void supportsHttpByteRanges() throws Exception {
        prepareCity("320100", "南京", new byte[] {1, 2, 3, 4, 5});
        try (var context = basemapContext()) {
            MockMvcBuilders.standaloneSetup(context.getBean(BasemapController.class))
                    .build()
                    .perform(get("/maps/320100.pmtiles").header("Range", "bytes=1-2"))
                    .andExpect(status().isPartialContent())
                    .andExpect(header().string("Content-Range", "bytes 1-2/5"))
                    .andExpect(content().bytes(new byte[] {2, 3}));
        }
    }

    @Test
    void servesBoundaryAsGeoJsonOutsideTheResponseEnvelope() throws Exception {
        Path cityDirectory = prepareCity("320100", "南京", new byte[] {1});
        try (var context = basemapContext()) {
            MockMvcBuilders.standaloneSetup(context.getBean(CityBoundaryController.class))
                    .build()
                    .perform(get("/api/cities/320100/boundary"))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType("application/geo+json"))
                    .andExpect(content().bytes(Files.readAllBytes(cityDirectory.resolve("boundary.geojson"))));
        }
    }

    /** 按生产目录约定搭一个临时市目录：{根}/{代码}-{市名}/，内含地图包与边界。 */
    private Path prepareCity(String code, String name, byte[] pmtiles) {
        try {
            Path cityDirectory = Files.createDirectories(directory.resolve(code + "-" + name + "市"));
            Files.write(cityDirectory.resolve(name.toLowerCase() + "-z16.pmtiles"), pmtiles);
            Files.writeString(cityDirectory.resolve("boundary.geojson"), "{\"type\":\"FeatureCollection\",\"features\":[]}");
            return cityDirectory;
        } catch (Exception error) {
            throw new IllegalStateException("准备临时底图目录失败", error);
        }
    }

    private AnnotationConfigApplicationContext basemapContext() {
        // 用临时目录配置验证 @Value 与组件字段注入，不加载数据库或完整应用。
        var context = new AnnotationConfigApplicationContext();
        context.setEnvironment(new MockEnvironment().withProperty("guidepins.basemap.root", directory.toString()));
        context.register(BasemapController.class, CityBoundaryController.class, BasemapService.class,
                FileBasemapMapper.class);
        context.refresh();
        return context;
    }
}

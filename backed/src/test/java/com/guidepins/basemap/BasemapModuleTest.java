package com.guidepins.basemap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.guidepins.basemap.controller.BasemapController;
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

class BasemapModuleTest {
    @TempDir Path directory;

    @Test
    void readsConfiguredFileAndReturns404WhenMissing() throws Exception {
        Path file = directory.resolve("nanjing.pmtiles");
        byte[] bytes = {1, 2, 3, 4, 5};
        Files.write(file, bytes);
        try (var context = basemapContext(file);
                var input = context.getBean(BasemapService.class).nanjing().getInputStream()) {
            assertArrayEquals(bytes, input.readAllBytes());
        }
        assertThrows(ResourceNotFoundException.class,
                () -> new FileBasemapMapper(directory.resolve("missing.pmtiles").toString()).nanjing());
    }

    @Test
    void supportsHttpByteRanges() throws Exception {
        Path file = directory.resolve("nanjing.pmtiles");
        Files.write(file, new byte[] {1, 2, 3, 4, 5});
        try (var context = basemapContext(file)) {
            MockMvcBuilders.standaloneSetup(context.getBean(BasemapController.class))
                    .build()
                    .perform(get("/maps/nanjing/v1/nanjing-z16.pmtiles").header("Range", "bytes=1-2"))
                    .andExpect(status().isPartialContent())
                    .andExpect(header().string("Content-Range", "bytes 1-2/5"))
                    .andExpect(content().bytes(new byte[] {2, 3}));
        }
    }

    private AnnotationConfigApplicationContext basemapContext(Path file) {
        // 用临时文件配置验证 @Value 与组件字段注入，不加载数据库或完整应用。
        var context = new AnnotationConfigApplicationContext();
        context.setEnvironment(new MockEnvironment().withProperty("guidepins.basemap.file", file.toString()));
        context.register(BasemapController.class, BasemapService.class, FileBasemapMapper.class);
        context.refresh();
        return context;
    }
}

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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class BasemapModuleTest {
    @TempDir Path directory;

    @Test
    void readsConfiguredFileAndReturns404WhenMissing() throws Exception {
        Path file = directory.resolve("nanjing.pmtiles");
        byte[] bytes = {1, 2, 3, 4, 5};
        Files.write(file, bytes);
        assertArrayEquals(bytes, new BasemapService(new FileBasemapMapper(file.toString()))
                .nanjing().getInputStream().readAllBytes());
        assertThrows(ResourceNotFoundException.class,
                () -> new FileBasemapMapper(directory.resolve("missing.pmtiles").toString()).nanjing());
    }

    @Test
    void supportsHttpByteRanges() throws Exception {
        Path file = directory.resolve("nanjing.pmtiles");
        Files.write(file, new byte[] {1, 2, 3, 4, 5});
        MockMvcBuilders.standaloneSetup(new BasemapController(new BasemapService(new FileBasemapMapper(file.toString()))))
                .build()
                .perform(get("/maps/nanjing/v1/nanjing-z16.pmtiles").header("Range", "bytes=1-2"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string("Content-Range", "bytes 1-2/5"))
                .andExpect(content().bytes(new byte[] {2, 3}));
    }
}

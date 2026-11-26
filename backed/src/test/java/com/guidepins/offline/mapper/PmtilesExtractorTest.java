package com.guidepins.offline.mapper;

import static org.junit.jupiter.api.Assertions.*;
import com.guidepins.city.domain.City;
import com.guidepins.publicmap.domain.MapPin;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 用固定 Hilbert 编号和带叶目录的归档验证提取，避免以实际大文件作为单元测试前提。 */
public class PmtilesExtractorTest {
    @TempDir Path directory;
    private static final City.Bounds CITY = new City.Bounds(118.3, 31.2, 119.2, 32.6);

    @Test void matchesIndependentHilbertFixtures() {
        assertEquals(0, PmtilesExtractor.tileId(0, 0, 0));
        assertEquals(4, PmtilesExtractor.tileId(1, 1, 0));
        assertEquals(2, PmtilesExtractor.tileId(1, 0, 1));
        assertEquals(18590, PmtilesExtractor.tileId(7, 106, 51));
        assertEquals(4873437245L, PmtilesExtractor.tileId(16, 54393, 26609));
    }

    @Test void selectsOverviewAndNearbyDetailAndDeduplicatesRepeatedPins() throws Exception {
        Path file = fixture(directory.resolve("test.pmtiles"));
        var pin = new MapPin(UUID.randomUUID(), "夫子庙", null, 118.79, 32.02, List.of());
        var extractor = new PmtilesExtractor();
        var empty = extractor.extract(file, CITY, List.of());
        assertEquals(List.of("7/106/51"), empty.tiles().keySet().stream().toList());
        var pack = extractor.extract(file, CITY, List.of(pin, pin));
        assertEquals(2, pack.tiles().size());
        assertTrue(pack.tiles().containsKey("16/54393/26609"));
        assertEquals(1, pack.blobs().size());
        assertEquals(1, pack.coverage().size());
    }

    @Test void rejectsTruncatedOrUnsupportedArchives() throws Exception {
        Path invalid = directory.resolve("invalid.pmtiles"); Files.write(invalid, new byte[12]);
        assertThrows(java.io.IOException.class, () -> new PmtilesExtractor().extract(invalid, CITY, List.of()));
        Path file = fixture(directory.resolve("unsupported.pmtiles"));
        byte[] raw = Files.readAllBytes(file); raw[98] = 4; Files.write(file, raw);
        assertThrows(java.io.IOException.class, () -> new PmtilesExtractor().extract(file, CITY, List.of()));
    }

    public static Path fixture(Path file) throws Exception {
        // 三片瓦片共享同一空 MVT，叶目录使用 gzip。远处高倍瓦片不能进入单点下载。
        long[] ids = {18590, 4873437245L, 4874000000L};
        var leaf = new ByteArrayOutputStream(); write(leaf, 3);
        long previous = 0; for (long id : ids) { write(leaf, id - previous); previous = id; }
        for (int i = 0; i < 3; i++) write(leaf, 1);
        for (int i = 0; i < 3; i++) write(leaf, 1);
        for (int i = 0; i < 3; i++) write(leaf, 1);
        byte[] leafBytes = gzip(leaf.toByteArray());
        var root = new ByteArrayOutputStream();
        for (long value : new long[] {1, 18590, 0, leafBytes.length, 1}) write(root, value);
        byte[] rootBytes = gzip(root.toByteArray());
        byte[] bytes = new byte[127 + rootBytes.length + leafBytes.length + 1];
        ByteBuffer header = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        header.put("PMTiles".getBytes(java.nio.charset.StandardCharsets.US_ASCII)); header.put((byte) 3);
        header.putLong(8, 127); header.putLong(16, rootBytes.length);
        header.putLong(40, 127 + rootBytes.length); header.putLong(48, leafBytes.length);
        header.putLong(56, bytes.length - 1); header.putLong(64, 1);
        bytes[97] = 2; bytes[98] = 1; bytes[99] = 1; bytes[100] = 7; bytes[101] = 16;
        System.arraycopy(rootBytes, 0, bytes, 127, rootBytes.length);
        System.arraycopy(leafBytes, 0, bytes, 127 + rootBytes.length, leafBytes.length);
        Files.createDirectories(file.getParent()); Files.write(file, bytes); return file;
    }
    private static byte[] gzip(byte[] bytes) throws Exception {
        var output = new ByteArrayOutputStream();
        try (var gzip = new GZIPOutputStream(output)) { gzip.write(bytes); }
        return output.toByteArray();
    }
    private static void write(ByteArrayOutputStream out, long value) {
        while (value >= 128) { out.write((int) (value & 127) | 128); value >>>= 7; }
        out.write((int) value);
    }
}

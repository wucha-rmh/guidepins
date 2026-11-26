package com.guidepins.offline.mapper;

import com.guidepins.city.domain.City;
import com.guidepins.publicmap.domain.MapPin;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.springframework.stereotype.Component;

/** 只读取现有 PMTiles v3：全市保留 z7～12，各图钉周边约两公里保留 z13～16。 */
@Component
public class PmtilesExtractor {
    public static final int OVERVIEW_ZOOM = 12;
    public static final int DETAIL_ZOOM = 16;
    public static final int RADIUS_METERS = 2000;
    private static final long MAX_BYTES = 64L * 1024 * 1024;

    public record Coverage(double west, double south, double east, double north) {}
    public record Pack(Map<String, Integer> tiles, List<String> blobs, int compression,
                       int minZoom, int overviewZoom, int detailZoom, int radiusMeters,
                       List<Coverage> coverage) {}
    private record Entry(long id, long offset, int length, long run) {}
    /** 范围或体积限制属于可向用户解释的生成条件，与文件损坏区分。 */
    public static class LimitExceededException extends IOException {
        LimitExceededException(String message) { super(message); }
    }

    public Pack extract(Path path, City.Bounds city, List<MapPin> pins) throws IOException {
        try (var archive = new Archive(path)) {
            var coverage = pins.stream().map(pin -> {
                double latitude = RADIUS_METERS / 111320.0;
                double longitude = latitude / Math.max(0.01, Math.cos(Math.toRadians(pin.latitude())));
                return new Coverage(pin.longitude() - longitude, pin.latitude() - latitude,
                        pin.longitude() + longitude, pin.latitude() + latitude);
            }).distinct().toList();
            Set<String> requested = new LinkedHashSet<>();
            addTiles(requested, new Coverage(city.west(), city.south(), city.east(), city.north()),
                    archive.minZoom, OVERVIEW_ZOOM);
            for (Coverage area : coverage) addTiles(requested, area, OVERVIEW_ZOOM + 1, DETAIL_ZOOM);
            Map<String, Integer> tiles = new LinkedHashMap<>();
            List<String> blobs = new ArrayList<>();
            Map<String, Integer> contents = new HashMap<>();
            long size = 0;
            for (String key : requested) {
                String[] coordinate = key.split("/");
                Entry entry = archive.find(tileId(Integer.parseInt(coordinate[0]),
                        Integer.parseInt(coordinate[1]), Integer.parseInt(coordinate[2])));
                if (entry == null) continue;
                // 重叠范围和归档内共用的瓦片内容只存一份，离线索引引用其编号。
                String contentKey = entry.offset + ":" + entry.length;
                Integer index = contents.get(contentKey);
                if (index == null) {
                    size += entry.length;
                    if (size > MAX_BYTES) throw new LimitExceededException("离线地图超出大小限制，请减少地点覆盖范围");
                    index = blobs.size();
                    blobs.add(Base64.getEncoder().encodeToString(archive.read(archive.dataOffset + entry.offset, entry.length)));
                    contents.put(contentKey, index);
                }
                tiles.put(key, index);
            }
            if (tiles.isEmpty()) throw new IOException("南京底图未包含所需瓦片");
            return new Pack(tiles, blobs, archive.tileCompression, archive.minZoom,
                    OVERVIEW_ZOOM, DETAIL_ZOOM, RADIUS_METERS, coverage);
        }
    }

    private static void addTiles(Set<String> keys, Coverage area, int min, int max) throws IOException {
        for (int z = min; z <= max; z++) {
            int west = tileX(area.west, z), east = tileX(area.east, z);
            int north = tileY(area.north, z), south = tileY(area.south, z);
            for (int x = west; x <= east; x++) for (int y = north; y <= south; y++) {
                keys.add(z + "/" + x + "/" + y);
                if (keys.size() > 100000) throw new LimitExceededException("地点覆盖范围过大，请减少地点后重新下载");
            }
        }
    }

    private static int tileX(double lon, int z) {
        int n = 1 << z;
        return Math.clamp((int) Math.floor((lon + 180) / 360 * n), 0, n - 1);
    }
    private static int tileY(double lat, int z) {
        double rad = Math.toRadians(Math.clamp(lat, -85.051128, 85.051128));
        int n = 1 << z;
        return Math.clamp((int) Math.floor((1 - Math.log(Math.tan(rad) + 1 / Math.cos(rad)) / Math.PI) / 2 * n), 0, n - 1);
    }

    /** PMTiles 使用 Hilbert 顺序编号；按 v3 规范换算，不能用普通行列号代替。 */
    static long tileId(int z, int x, int y) {
        long id = ((1L << (2 * z)) - 1) / 3;
        for (int s = 1 << (z - 1); s > 0; s >>= 1) {
            int rx = x & s, ry = y & s;
            id += (long) ((3 * rx) ^ ry) * s;
            if (ry == 0) {
                if (rx != 0) { x = s - 1 - x; y = s - 1 - y; }
                int swap = x; x = y; y = swap;
            }
        }
        return id;
    }

    /** 每次导出独占文件句柄与目录缓存，不把私人地图内容留在共享缓存中。 */
    private static class Archive implements AutoCloseable {
        final RandomAccessFile file;
        final long rootOffset, rootLength, leafOffset, dataOffset;
        final int internalCompression, tileCompression, minZoom;
        final Map<String, List<Entry>> directories = new HashMap<>();
        Archive(Path path) throws IOException {
            file = new RandomAccessFile(path.toFile(), "r");
            try {
                byte[] raw = read(0, 127);
                ByteBuffer h = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
                if (!new String(raw, 0, 7, java.nio.charset.StandardCharsets.US_ASCII).equals("PMTiles") || raw[7] != 3)
                    throw new IOException("仅支持 PMTiles v3 底图");
                rootOffset = h.getLong(8); rootLength = h.getLong(16);
                leafOffset = h.getLong(40); dataOffset = h.getLong(56);
                internalCompression = raw[97]; tileCompression = raw[98]; minZoom = raw[100];
                if ((internalCompression != 1 && internalCompression != 2) ||
                        (tileCompression != 1 && tileCompression != 2) || raw[99] != 1 || minZoom > OVERVIEW_ZOOM)
                    throw new IOException("底图压缩或瓦片格式不支持离线导出");
            } catch (IOException | RuntimeException error) { file.close(); throw error; }
        }
        byte[] read(long offset, long length) throws IOException {
            if (offset < 0 || length < 0 || length > 16 * 1024 * 1024 || offset > file.length() - length)
                throw new IOException("底图文件已损坏");
            byte[] data = new byte[(int) length]; file.seek(offset); file.readFully(data); return data;
        }
        List<Entry> directory(long offset, long length) throws IOException {
            String key = offset + ":" + length;
            if (directories.containsKey(key)) return directories.get(key);
            byte[] data = read(offset, length);
            if (internalCompression == 2) try (var gzip = new GZIPInputStream(new ByteArrayInputStream(data))) {
                data = gzip.readNBytes(16 * 1024 * 1024 + 1);
                if (data.length > 16 * 1024 * 1024) throw new IOException("底图目录过大");
            }
            var input = new ByteArrayInputStream(data);
            int count = Math.toIntExact(varint(input));
            if (count < 0 || count > 1000000) throw new IOException("底图目录无效");
            long[] ids = new long[count], runs = new long[count], lengths = new long[count], offsets = new long[count];
            for (int i = 0; i < count; i++) ids[i] = (i == 0 ? 0 : ids[i - 1]) + varint(input);
            for (int i = 0; i < count; i++) runs[i] = varint(input);
            for (int i = 0; i < count; i++) lengths[i] = varint(input);
            for (int i = 0; i < count; i++) {
                long value = varint(input);
                offsets[i] = value == 0 && i > 0 ? offsets[i - 1] + lengths[i - 1] : value - 1;
            }
            List<Entry> result = new ArrayList<>(count);
            for (int i = 0; i < count; i++) result.add(new Entry(ids[i], offsets[i], Math.toIntExact(lengths[i]), runs[i]));
            directories.put(key, result); return result;
        }
        Entry find(long id) throws IOException {
            long offset = rootOffset, length = rootLength;
            for (int depth = 0; depth < 4; depth++) {
                List<Entry> entries = directory(offset, length);
                int low = 0, high = entries.size() - 1;
                while (low <= high) {
                    int mid = (low + high) >>> 1;
                    if (entries.get(mid).id <= id) low = mid + 1; else high = mid - 1;
                }
                if (high < 0) return null;
                Entry entry = entries.get(high);
                if (entry.run > 0) return id - entry.id < entry.run ? entry : null;
                offset = leafOffset + entry.offset; length = entry.length;
            }
            throw new IOException("底图目录嵌套过深");
        }
        private static long varint(ByteArrayInputStream input) throws IOException {
            long value = 0;
            for (int shift = 0; shift < 63; shift += 7) {
                int b = input.read();
                if (b < 0) throw new IOException("底图目录被截断");
                value |= (long) (b & 127) << shift;
                if ((b & 128) == 0) return value;
            }
            throw new IOException("底图目录整数无效");
        }
        @Override public void close() throws IOException { file.close(); }
    }
}

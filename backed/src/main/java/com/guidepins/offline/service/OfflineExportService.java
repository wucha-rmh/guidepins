package com.guidepins.offline.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidepins.auth.domain.AccountIdentity;
import com.guidepins.basemap.service.BasemapService;
import com.guidepins.exception.ApiException;
import com.guidepins.offline.mapper.PmtilesExtractor;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.zip.GZIPOutputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** 生成自包含 HTML；先鉴权取快照，后读取底图，不缓存任何私人地图下载结果。 */
@Service
public class OfflineExportService {
    @Autowired private OfflineSnapshotService snapshots;
    @Autowired private BasemapService basemaps;
    @Autowired private PmtilesExtractor extractor;
    @Autowired private ObjectMapper json;
    private final Semaphore exporting = new Semaphore(2);
    public record Download(String name, byte[] bytes) {}

    public Download export(UUID mapId, AccountIdentity viewer) {
        var snapshot = snapshots.read(mapId, viewer);
        if (!exporting.tryAcquire()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "正在生成其他离线地图，请稍后重试");
        try {
            var template = new ClassPathResource("offline/viewer.html");
            if (!template.exists()) throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "离线查看器尚未构建，请维护者执行 npm run build:offline");
            String html;
            try (var input = template.getInputStream()) { html = new String(input.readAllBytes(), StandardCharsets.UTF_8); }
            var pack = extractor.extract(basemaps.pmtiles("320100").getFile().toPath(), snapshot.city().bounds(), snapshot.pins());
            var compressed = new ByteArrayOutputStream();
            // 合并压缩索引、内容与瓦片，减少 JSON 和逐片 Base64 的额外占用。
            try (var gzip = new GZIPOutputStream(compressed)) {
                json.writeValue(gzip, Map.of("snapshot", snapshot, "basemap", pack));
            }
            html = html.replace("__GUIDEPINS_PAYLOAD__", Base64.getEncoder().encodeToString(compressed.toByteArray()));
            return new Download("拾点地图-南京-" + mapId + ".html", html.getBytes(StandardCharsets.UTF_8));
        } catch (PmtilesExtractor.LimitExceededException error) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, error.getMessage());
        } catch (IOException error) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "离线文件生成失败，请检查南京底图文件后重试");
        } finally { exporting.release(); }
    }
}

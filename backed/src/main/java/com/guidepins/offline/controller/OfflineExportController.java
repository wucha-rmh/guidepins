package com.guidepins.offline.controller;

import com.guidepins.auth.domain.AccountIdentity;
import com.guidepins.offline.service.OfflineExportService;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 下载按地图读取权限开放给访客；成功为文件，错误仍使用统一 JSON 响应。 */
@RestController
public class OfflineExportController {
    @Autowired private OfflineExportService exports;

    @GetMapping("/api/maps/{mapId}/offline")
    ResponseEntity<byte[]> download(@PathVariable UUID mapId, @AuthenticationPrincipal AccountIdentity viewer) {
        var file = exports.export(mapId, viewer);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.name(), StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .contentLength(file.bytes().length).body(file.bytes());
    }
}

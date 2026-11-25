package com.guidepins.basemap.controller;

import com.guidepins.basemap.service.BasemapService;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 提供地市行政边界，供前端按所选城市裁剪视野与绘制市界。
 *
 * <p>响应使用 {@code application/geo+json}，走 Resource 转换器，不会被统一响应外壳包装。
 */
@RestController
@RequestMapping("/api/cities")
public class CityBoundaryController {
    private static final MediaType GEO_JSON = MediaType.parseMediaType("application/geo+json");

    @Autowired
    private BasemapService basemaps;

    @GetMapping(value = "/{code:\\d{6}}/boundary", produces = "application/geo+json")
    ResponseEntity<Resource> boundary(@PathVariable String code) {
        // 边界文件随地图数据一起更新，缓存一天；文件缺失时按 404 返回。
        return ResponseEntity.ok()
                .contentType(GEO_JSON)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
                .body(basemaps.boundary(code));
    }
}

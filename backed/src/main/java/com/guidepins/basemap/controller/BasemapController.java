package com.guidepins.basemap.controller;

import com.guidepins.basemap.service.BasemapService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** 按城市行政区代码提供 PMTiles 地图包，路径与 city.basemap_path 保持一致。 */
@RestController
public class BasemapController {
    @Autowired
    private BasemapService basemaps;

    @GetMapping("/maps/{code:\\d{6}}.pmtiles")
    ResponseEntity<Resource> pmtiles(@PathVariable String code) {
        // 返回文件 Resource，由 Spring MVC 处理浏览器的 Range 请求和 206 响应。
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).body(basemaps.pmtiles(code));
    }
}

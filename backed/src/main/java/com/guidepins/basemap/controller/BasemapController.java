package com.guidepins.basemap.controller;

import com.guidepins.basemap.service.BasemapService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 以数据库南京 basemap_path 所记录的 URL 提供 PMTiles 地图包。 */
@RestController
public class BasemapController {
    @Autowired
    private BasemapService basemaps;

    @GetMapping("/maps/nanjing/v1/nanjing-z16.pmtiles")
    ResponseEntity<Resource> nanjing() {
        // 返回文件 Resource，由 Spring MVC 处理浏览器的 Range 请求和 206 响应。
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM).body(basemaps.nanjing());
    }
}

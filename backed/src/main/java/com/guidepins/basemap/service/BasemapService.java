package com.guidepins.basemap.service;

import com.guidepins.basemap.mapper.BasemapMapper;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/** 底图读取的业务入口，文件位置与可读性由文件 Mapper 负责。 */
@Service
public class BasemapService {
    private final BasemapMapper basemaps;

    public BasemapService(BasemapMapper basemaps) {
        this.basemaps = basemaps;
    }

    public Resource nanjing() {
        return basemaps.nanjing();
    }
}

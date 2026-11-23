package com.guidepins.basemap.mapper;

import org.springframework.core.io.Resource;

/** 地图包读取接口，便于服务层独立于本地文件路径。 */
public interface BasemapMapper {
    Resource nanjing();
}

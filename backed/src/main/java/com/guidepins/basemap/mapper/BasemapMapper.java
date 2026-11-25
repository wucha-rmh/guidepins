package com.guidepins.basemap.mapper;

import org.springframework.core.io.Resource;

/** 地图包读取接口，便于服务层独立于本地文件路径。 */
public interface BasemapMapper {
    /** 按行政区代码读取该市的 PMTiles 地图包。 */
    Resource pmtiles(String code);

    /** 按行政区代码读取该市的行政边界 GeoJSON，供前端裁剪视野和描边。 */
    Resource boundary(String code);
}

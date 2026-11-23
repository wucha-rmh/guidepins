package com.guidepins.city.domain;

import java.util.UUID;

/** 地市接口数据；bounds 是 WGS84 范围，经度、纬度分别对应西东和南北边界。 */
public record City(UUID id, String code, String name, String basemapPath, Bounds bounds) {
    public record Bounds(double west, double south, double east, double north) {}
}

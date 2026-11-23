package com.guidepins.publicmap.domain;

import java.util.UUID;

/** 对外展示的图钉：WGS84 坐标按经度、纬度顺序返回。 */
public record MapPin(UUID id, String name, String description, double longitude, double latitude) {}

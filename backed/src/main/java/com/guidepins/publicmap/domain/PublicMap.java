package com.guidepins.publicmap.domain;

import java.util.UUID;

/** 公共地图列表项；阶段一每张地图归属一个地市。 */
public record PublicMap(UUID id, UUID cityId, String name) {}

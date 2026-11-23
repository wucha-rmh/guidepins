package com.guidepins.publicmap.domain;

import java.util.List;
import java.util.UUID;

/** 地图详情响应，包含当前地图的全部图钉。 */
public record PublicMapDetail(UUID id, UUID cityId, String name, List<MapPin> pins) {}

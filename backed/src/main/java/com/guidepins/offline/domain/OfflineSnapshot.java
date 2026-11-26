package com.guidepins.offline.domain;

import com.guidepins.city.domain.City;
import com.guidepins.publicmap.domain.GuideDetail;
import com.guidepins.publicmap.domain.MapPin;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 离线内容契约；不包含账号、会话及密码，版本号用于以后兼容已有离线文件。 */
public record OfflineSnapshot(int version, UUID mapId, String name, Instant mapUpdatedAt,
                              Instant exportedAt, City city, List<MapPin> pins, List<GuideDetail> guides) {}

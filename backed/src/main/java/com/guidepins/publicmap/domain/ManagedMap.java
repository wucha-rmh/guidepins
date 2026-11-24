package com.guidepins.publicmap.domain;

import java.time.Instant;
import java.util.UUID;

/** 地图管理响应与业务记录；归属和时间由服务端维护。 */
public record ManagedMap(UUID id, UUID cityId, String name, UUID ownerId, String visibility,
                         Instant createdAt, Instant updatedAt) {}

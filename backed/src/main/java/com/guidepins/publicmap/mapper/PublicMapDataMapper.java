package com.guidepins.publicmap.mapper;

import com.guidepins.publicmap.domain.MapPin;
import com.guidepins.publicmap.domain.PublicMap;
import com.guidepins.publicmap.domain.ManagedMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 公共地图的数据读取接口，按地市或地图编号限定读取范围。 */
public interface PublicMapDataMapper {
    List<PublicMap> findByCityId(UUID cityId);
    Optional<ManagedMap> findById(UUID id, UUID viewerId);
    List<MapPin> findPinsByMapId(UUID mapId, UUID viewerId);
    List<ManagedMap> findOwnedByCity(UUID cityId, UUID ownerId);
    Optional<ManagedMap> create(UUID cityId, UUID ownerId, String name, String visibility);
    Optional<ManagedMap> updateName(UUID id, UUID actorId, String name);
    Optional<ManagedMap> updateVisibility(UUID id, UUID ownerId, String visibility);

    // 阶段三：图钉写入由下层 SQL 在同一条语句里校验权限，并顺带推进地图更新时间。
    Optional<MapPin> findPin(UUID mapId, UUID pinId, UUID viewerId);
    Optional<MapPin> insertPin(UUID mapId, UUID actorId, String name, String description,
                               double longitude, double latitude);
    Optional<MapPin> updatePin(UUID mapId, UUID pinId, UUID actorId, String name, String description,
                               double longitude, double latitude);
    boolean deletePin(UUID mapId, UUID pinId, UUID actorId);
}

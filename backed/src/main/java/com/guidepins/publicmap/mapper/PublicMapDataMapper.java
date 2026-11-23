package com.guidepins.publicmap.mapper;

import com.guidepins.publicmap.domain.MapPin;
import com.guidepins.publicmap.domain.PublicMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 公共地图的数据读取接口，按地市或地图编号限定读取范围。 */
public interface PublicMapDataMapper {
    List<PublicMap> findByCityId(UUID cityId);
    Optional<PublicMap> findById(UUID id);
    List<MapPin> findPinsByMapId(UUID mapId);
}

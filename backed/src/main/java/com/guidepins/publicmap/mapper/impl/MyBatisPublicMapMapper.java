package com.guidepins.publicmap.mapper.impl;

import com.guidepins.publicmap.mapper.PublicMapDataMapper;
import com.guidepins.publicmap.domain.MapPin;
import com.guidepins.publicmap.mapper.entity.MapPinRow;
import com.guidepins.publicmap.domain.PublicMap;
import com.guidepins.publicmap.domain.ManagedMap;
import com.guidepins.publicmap.mapper.entity.TravelMapRow;
import com.guidepins.publicmap.mapper.mybatis.MapPinSqlMapper;
import com.guidepins.publicmap.mapper.mybatis.TravelMapSqlMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/** 分别读取地图和图钉，再转换为不含数据库内部关联列的接口数据。 */
@Repository
public class MyBatisPublicMapMapper implements PublicMapDataMapper {

    @Autowired
    private TravelMapSqlMapper maps;

    @Autowired
    private MapPinSqlMapper pins;

    @Override
    public List<PublicMap> findByCityId(UUID cityId) {
        // 公共列表由 SQL 强制过滤 visibility，不混入登录用户自己的私人地图。
        return maps.selectByCity(cityId)
                .stream().map(MyBatisPublicMapMapper::toMap).toList();
    }

    @Override
    public Optional<ManagedMap> findById(UUID id, UUID viewerId) {
        return Optional.ofNullable(maps.selectMapById(id, viewerId)).map(MyBatisPublicMapMapper::toManagedMap);
    }

    @Override
    public List<MapPin> findPinsByMapId(UUID mapId, UUID viewerId) {
        // 图钉查询再次关联地图校验可见性，子资源不能绕过地图权限。
        return pins.selectByMap(mapId, viewerId)
                .stream().map(MyBatisPublicMapMapper::toPin).toList();
    }

    @Override
    public List<ManagedMap> findOwnedByCity(UUID cityId, UUID ownerId) {
        return maps.selectOwnedByCity(cityId, ownerId).stream().map(MyBatisPublicMapMapper::toManagedMap).toList();
    }

    @Override
    public Optional<ManagedMap> create(UUID cityId, UUID ownerId, String name, String visibility) {
        return Optional.ofNullable(maps.insertMap(cityId, ownerId, name, visibility))
                .map(MyBatisPublicMapMapper::toManagedMap);
    }

    @Override
    public Optional<ManagedMap> updateName(UUID id, UUID actorId, String name) {
        return Optional.ofNullable(maps.updateName(id, actorId, name)).map(MyBatisPublicMapMapper::toManagedMap);
    }

    @Override
    public Optional<ManagedMap> updateVisibility(UUID id, UUID ownerId, String visibility) {
        return Optional.ofNullable(maps.updateVisibility(id, ownerId, visibility))
                .map(MyBatisPublicMapMapper::toManagedMap);
    }

    @Override
    public Optional<MapPin> findPin(UUID mapId, UUID pinId, UUID viewerId) {
        return Optional.ofNullable(pins.selectPin(mapId, pinId, viewerId)).map(MyBatisPublicMapMapper::toPin);
    }

    @Override
    public Optional<MapPin> insertPin(UUID mapId, UUID actorId, String name, String description,
                                      double longitude, double latitude) {
        MapPinRow row = pins.insertPin(mapId, actorId, name, description, longitude, latitude);
        if (row == null) {
            return Optional.empty();
        }
        // 新增总会改变地图内容：先写入图钉，再推进地图更新时间。
        pins.touchMap(mapId, actorId);
        return Optional.of(toPin(row));
    }

    @Override
    public Optional<MapPin> updatePin(UUID mapId, UUID pinId, UUID actorId, String name, String description,
                                      double longitude, double latitude) {
        // 更新时间判断需要旧值，因此必须在图钉更新之前执行；失败时由事务整体回滚。
        pins.touchMapOnPinChange(mapId, pinId, actorId, name, description, longitude, latitude);
        return Optional.ofNullable(pins.updatePin(mapId, pinId, actorId, name, description, longitude, latitude))
                .map(MyBatisPublicMapMapper::toPin);
    }

    @Override
    public boolean deletePin(UUID mapId, UUID pinId, UUID actorId) {
        if (pins.deletePin(mapId, pinId, actorId) == null) {
            return false;
        }
        pins.touchMap(mapId, actorId);
        return true;
    }

    private static ManagedMap toManagedMap(TravelMapRow row) {
        return new ManagedMap(row.getId(), row.getCityId(), row.getName(), row.getOwnerId(), row.getVisibility(),
                row.getCreatedAt(), row.getUpdatedAt());
    }

    private static PublicMap toMap(TravelMapRow row) {
        return new PublicMap(row.getId(), row.getCityId(), row.getName());
    }

    private static MapPin toPin(MapPinRow row) {
        return new MapPin(row.getId(), row.getName(), row.getDescription(), row.getLongitude(), row.getLatitude());
    }
}

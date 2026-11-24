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

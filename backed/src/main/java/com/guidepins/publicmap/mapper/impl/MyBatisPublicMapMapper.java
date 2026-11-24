package com.guidepins.publicmap.mapper.impl;

import com.guidepins.publicmap.mapper.PublicMapDataMapper;
import com.guidepins.publicmap.domain.MapPin;
import com.guidepins.publicmap.mapper.entity.MapPinRow;
import com.guidepins.publicmap.domain.PublicMap;
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
        // 当前表中的地图均为公共地图；阶段二加入私人地图前须增加可见性过滤。
        return maps.selectByCity(cityId)
                .stream().map(MyBatisPublicMapMapper::toMap).toList();
    }

    @Override
    public Optional<PublicMap> findById(UUID id) {
        return Optional.ofNullable(maps.selectMapById(id)).map(MyBatisPublicMapMapper::toMap);
    }

    @Override
    public List<MapPin> findPinsByMapId(UUID mapId) {
        // 限定 map_id，避免把其他地图的图钉带入当前详情。
        return pins.selectByMap(mapId)
                .stream().map(MyBatisPublicMapMapper::toPin).toList();
    }

    private static PublicMap toMap(TravelMapRow row) {
        return new PublicMap(row.getId(), row.getCityId(), row.getName());
    }

    private static MapPin toPin(MapPinRow row) {
        return new MapPin(row.getId(), row.getName(), row.getDescription(), row.getLongitude(), row.getLatitude());
    }
}

package com.guidepins.publicmap.mapper.mybatis;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.guidepins.publicmap.mapper.entity.MapPinRow;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 图钉读取与写入 SQL 定义在 mapper/publicmap/MapPinSqlMapper.xml。 */
@Mapper
public interface MapPinSqlMapper extends BaseMapper<MapPinRow> {
    List<MapPinRow> selectByMap(@Param("mapId") UUID mapId, @Param("viewerId") UUID viewerId);

    MapPinRow selectPin(@Param("mapId") UUID mapId, @Param("pinId") UUID pinId, @Param("viewerId") UUID viewerId);

    MapPinRow insertPin(@Param("mapId") UUID mapId, @Param("actorId") UUID actorId, @Param("name") String name,
                        @Param("description") String description, @Param("longitude") double longitude,
                        @Param("latitude") double latitude);

    MapPinRow updatePin(@Param("mapId") UUID mapId, @Param("pinId") UUID pinId, @Param("actorId") UUID actorId,
                        @Param("name") String name, @Param("description") String description,
                        @Param("longitude") double longitude, @Param("latitude") double latitude);

    UUID deletePin(@Param("mapId") UUID mapId, @Param("pinId") UUID pinId, @Param("actorId") UUID actorId);

    /* 更新时间推进语句带 RETURNING，未命中时返回 null，因此用包装类型而非 int。 */
    Integer touchMapOnPinChange(@Param("mapId") UUID mapId, @Param("pinId") UUID pinId, @Param("actorId") UUID actorId,
                                @Param("name") String name, @Param("description") String description,
                                @Param("longitude") double longitude, @Param("latitude") double latitude);

    Integer touchMap(@Param("mapId") UUID mapId, @Param("actorId") UUID actorId);
}

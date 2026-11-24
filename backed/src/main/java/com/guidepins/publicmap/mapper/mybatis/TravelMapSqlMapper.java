package com.guidepins.publicmap.mapper.mybatis;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.guidepins.publicmap.mapper.entity.TravelMapRow;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 公共地图读取 SQL 定义在 mapper/publicmap/TravelMapSqlMapper.xml。 */
@Mapper
public interface TravelMapSqlMapper extends BaseMapper<TravelMapRow> {
    List<TravelMapRow> selectByCity(@Param("cityId") UUID cityId);

    TravelMapRow selectMapById(@Param("id") UUID id, @Param("viewerId") UUID viewerId);
    List<TravelMapRow> selectOwnedByCity(@Param("cityId") UUID cityId, @Param("ownerId") UUID ownerId);
    TravelMapRow insertMap(@Param("cityId") UUID cityId, @Param("ownerId") UUID ownerId,
                          @Param("name") String name, @Param("visibility") String visibility);
    TravelMapRow updateName(@Param("id") UUID id, @Param("actorId") UUID actorId, @Param("name") String name);
    TravelMapRow updateVisibility(@Param("id") UUID id, @Param("ownerId") UUID ownerId,
                                 @Param("visibility") String visibility);
}
